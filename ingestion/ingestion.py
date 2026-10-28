import json
import html
import logging
import os
import re
import signal
import time
from datetime import datetime, timezone
from typing import Any

import requests
from apscheduler.schedulers.blocking import BlockingScheduler
from bs4 import BeautifulSoup


LOG = logging.getLogger("ingestion")
API_BASE_URL = os.getenv("JAVA_API_URL", "http://app:8080").rstrip("/")
API_KEY = os.getenv("INGESTION_API_KEY", "")
INTERVAL_SECONDS = int(os.getenv("INGESTION_INTERVAL_SECONDS", "3600"))
REQUEST_TIMEOUT_SECONDS = int(os.getenv("INGESTION_REQUEST_TIMEOUT_SECONDS", "30"))
ENV_PATTERN = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")
HTML_TAG_PATTERN = re.compile(r"</?[A-Za-z][^>]*>")


class IngestionError(Exception):
    pass


def _read_path(value: Any, path: str) -> Any:
    current = value
    for part in path.split(".") if path else ():
        if isinstance(current, list) and part.isdigit():
            index = int(part)
            current = current[index] if index < len(current) else None
        elif isinstance(current, dict):
            current = current.get(part)
        else:
            return None
    return current


def _text(value: Any, field: str, required: bool = True, max_length: int | None = None) -> str | None:
    if value is None:
        if required:
            raise IngestionError(f"Brak wymaganej wartości: {field}")
        return None
    raw = str(value)
    if HTML_TAG_PATTERN.search(raw):
        soup = BeautifulSoup(raw, "html.parser")
        for element in soup(["script", "style"]):
            element.decompose()
        text = soup.get_text(" ", strip=True)
    else:
        text = html.unescape(raw)
    text = " ".join(text.split())
    if required and not text:
        raise IngestionError(f"Pusta wymagana wartość: {field}")
    if max_length is not None:
        text = text[:max_length]
    return text or None


def _deadline(value: Any) -> str:
    if not isinstance(value, str) or not value.strip():
        raise IngestionError("Brak terminu składania ofert")
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError as error:
        raise IngestionError("Termin musi być datą ISO-8601") from error
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
    return parsed.isoformat(timespec="seconds")


def _expand_environment(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: _expand_environment(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_expand_environment(item) for item in value]
    if isinstance(value, str):
        def replace(match: re.Match[str]) -> str:
            name = match.group(1)
            secret = os.getenv(name)
            if not secret:
                raise IngestionError(f"Brak wymaganej zmiennej środowiskowej: {name}")
            return secret

        return ENV_PATTERN.sub(replace, value)
    return value


def _records(payload: Any, item_path: str) -> list[dict[str, Any]]:
    items = _read_path(payload, item_path)
    if not item_path:
        items = payload
    if not isinstance(items, list):
        raise IngestionError("Odpowiedź API musi zawierać tablicę ogłoszeń pod wskazaną ścieżką")
    if any(not isinstance(item, dict) for item in items):
        raise IngestionError("Każde ogłoszenie API musi być obiektem JSON")
    return items


def _map_tender(source: dict[str, Any], item: dict[str, Any], fields: dict[str, str]) -> dict[str, Any]:
    required = {
        "zewnetrzneId": ("zewnetrzneId", 200),
        "tytul": ("tytul", 160),
        "opis": ("opis", 20000),
        "kategoria": ("kategoria", 100),
        "lokalizacja": ("lokalizacja", 160),
    }
    tender: dict[str, Any] = {"zrodloId": source["id"]}
    for target, (label, limit) in required.items():
        path = fields.get(target)
        if not path:
            raise IngestionError(f"Brak mapowania fields.{target} w konfiguracji źródła")
        tender[target] = _text(_read_path(item, path), label, max_length=limit)

    deadline_path = fields.get("terminSkladaniaOfert")
    if not deadline_path:
        raise IngestionError("Brak mapowania fields.terminSkladaniaOfert w konfiguracji źródła")
    tender["terminSkladaniaOfert"] = _deadline(_read_path(item, deadline_path))
    address_path = fields.get("adresZrodla")
    tender["adresZrodla"] = (
        _text(_read_path(item, address_path), "adresZrodla", required=False, max_length=2048)
        if address_path
        else None
    )
    return tender


def _source_request(source: dict[str, Any], config: dict[str, Any]) -> Any:
    url = source["adresEndpointu"]
    headers = _expand_environment(config.get("headers", {}))
    if not isinstance(headers, dict):
        raise IngestionError("Konfiguracja headers musi być obiektem JSON")
    headers = {str(key): str(value) for key, value in headers.items()}
    headers.setdefault("Accept", "application/json")
    method = source["metoda"].upper()
    request_options: dict[str, Any] = {
        "headers": headers,
        "timeout": REQUEST_TIMEOUT_SECONDS,
    }
    query = config.get("query")
    if query is not None:
        if not isinstance(query, dict):
            raise IngestionError("Konfiguracja query musi być obiektem JSON")
        request_options["params"] = query
    if method == "POST":
        body = config.get("body", {})
        if not isinstance(body, dict):
            raise IngestionError("Konfiguracja body dla POST musi być obiektem JSON")
        request_options["json"] = body
    elif method != "GET":
        raise IngestionError(f"Nieobsługiwana metoda źródła: {method}")
    response = requests.request(method, url, **request_options)
    response.raise_for_status()
    try:
        return response.json()
    except requests.exceptions.JSONDecodeError as error:
        raise IngestionError("Źródło nie zwróciło poprawnego JSON") from error


def poll_sources() -> None:
    if not API_KEY:
        raise IngestionError("Brak wymaganej zmiennej INGESTION_API_KEY")
    headers = {"X-Ingestion-Key": API_KEY}
    try:
        sources_response = requests.get(
            f"{API_BASE_URL}/api/internal/sources",
            headers=headers,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        sources_response.raise_for_status()
        sources = sources_response.json()
    except (requests.RequestException, ValueError) as error:
        LOG.error("Nie udało się pobrać konfiguracji źródeł z Java API: %s", error)
        return
    if not isinstance(sources, list):
        raise IngestionError("Java API zwróciło nieprawidłową listę źródeł")

    for source in sources:
        try:
            config = json.loads(source.get("parametryJson") or "{}")
            if not isinstance(config, dict):
                raise IngestionError("Konfiguracja źródła musi być obiektem JSON")
            payload = _source_request(source, config)
            items = _records(payload, str(config.get("itemsPath", "")))
            fields = config.get("fields")
            if not isinstance(fields, dict):
                raise IngestionError("Brak obiektu mapowań fields w konfiguracji źródła")
            imported = 0
            for item in items:
                try:
                    tender = _map_tender(source, item, fields)
                    result = requests.post(
                        f"{API_BASE_URL}/api/internal/tenders",
                        headers={**headers, "Content-Type": "application/json"},
                        json=tender,
                        timeout=REQUEST_TIMEOUT_SECONDS,
                    )
                    result.raise_for_status()
                    imported += 1
                except (IngestionError, requests.RequestException, ValueError) as error:
                    LOG.warning("Pominięto ogłoszenie ze źródła %s: %s", source.get("nazwa"), error)
            LOG.info("Źródło %s: wysłano %d z %d ogłoszeń", source.get("nazwa"), imported, len(items))
        except (IngestionError, requests.RequestException, ValueError, KeyError, TypeError) as error:
            LOG.error("Nie udało się pobrać źródła %s: %s", source.get("nazwa", source.get("id")), error)


def wait_for_java_api() -> None:
    if not API_KEY:
        raise IngestionError("Brak wymaganej zmiennej INGESTION_API_KEY")
    deadline = time.monotonic() + 180
    while True:
        try:
            response = requests.get(
                f"{API_BASE_URL}/api/internal/sources",
                headers={"X-Ingestion-Key": API_KEY},
                timeout=REQUEST_TIMEOUT_SECONDS,
            )
            if response.status_code in (401, 403, 503):
                raise IngestionError(
                    f"Java API odrzuca konfigurację importu (HTTP {response.status_code}); "
                    "sprawdź INGESTION_API_KEY"
                )
            response.raise_for_status()
            return
        except requests.RequestException as error:
            if time.monotonic() >= deadline:
                raise IngestionError("Java API nie uruchomiło się w ciągu 180 sekund") from error
            LOG.info("Oczekiwanie na Java API: %s", error)
            time.sleep(5)


def main() -> None:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    if INTERVAL_SECONDS < 60:
        raise ValueError("INGESTION_INTERVAL_SECONDS musi wynosić co najmniej 60")
    wait_for_java_api()
    poll_sources()
    scheduler = BlockingScheduler(timezone="UTC")
    scheduler.add_job(
        poll_sources,
        "interval",
        seconds=INTERVAL_SECONDS,
        id="poll-configured-sources",
        max_instances=1,
        coalesce=True,
    )
    signal.signal(signal.SIGTERM, lambda *_: scheduler.shutdown(wait=False))
    LOG.info("Harmonogram uruchomiony; interwał: %d s", INTERVAL_SECONDS)
    scheduler.start()


if __name__ == "__main__":
    main()
