import json
import unittest
from unittest.mock import Mock, patch

import ingestion
from ingestion import IngestionError, _deadline, _map_tender, _records, _text


class IngestionTests(unittest.TestCase):
    def test_extracts_items_from_nested_path(self):
        self.assertEqual(_records({"data": {"items": [{"id": 1}]}}, "data.items"), [{"id": 1}])

    def test_rejects_non_list_response(self):
        with self.assertRaises(IngestionError):
            _records({"items": {}}, "items")

    def test_cleans_html_and_normalizes_whitespace(self):
        self.assertEqual(_text("<p>Nowy&nbsp; <b>przetarg</b></p>", "tytul"), "Nowy przetarg")

    def test_requires_mapped_values(self):
        with self.assertRaises(IngestionError):
            _text("  ", "tytul")

    def test_normalizes_timezone_to_utc_local_datetime(self):
        self.assertEqual(_deadline("2026-03-01T13:00:00+01:00"), "2026-03-01T12:00:00")

    def test_rejects_invalid_deadline(self):
        with self.assertRaises(IngestionError):
            _deadline("not-a-date")

    def test_maps_source_fields_to_java_contract(self):
        source = {"id": "source-1"}
        item = {
            "id": 42,
            "title": "<b>Usługi IT</b>",
            "description": "Opis",
            "category": "IT",
            "city": "Warszawa",
            "deadline": "2026-10-28T15:00:00Z",
            "url": "https://example.test/tender/42",
        }
        fields = {
            "zewnetrzneId": "id",
            "tytul": "title",
            "opis": "description",
            "kategoria": "category",
            "lokalizacja": "city",
            "terminSkladaniaOfert": "deadline",
            "adresZrodla": "url",
        }
        mapped = _map_tender(source, item, fields)
        self.assertEqual(mapped["zrodloId"], "source-1")
        self.assertEqual(mapped["tytul"], "Usługi IT")
        self.assertEqual(mapped["terminSkladaniaOfert"], "2026-10-28T15:00:00")
        self.assertEqual(mapped["adresZrodla"], "https://example.test/tender/42")

    @patch.dict("os.environ", {"SOURCE_API_TOKEN": "secret-value"})
    @patch("ingestion.requests.request")
    def test_builds_configured_get_request(self, request):
        response = Mock()
        response.json.return_value = {"items": []}
        request.return_value = response
        source = {"adresEndpointu": "https://example.test/api", "metoda": "GET"}
        config = {
            "query": {"page": 2},
            "headers": {"Authorization": "Bearer ${SOURCE_API_TOKEN}"},
        }

        ingestion._source_request(source, config)

        request.assert_called_once_with(
            "GET",
            "https://example.test/api",
            headers={"Authorization": "Bearer secret-value", "Accept": "application/json"},
            params={"page": 2},
            timeout=30,
        )

    @patch("ingestion.requests.post")
    @patch("ingestion._source_request")
    @patch("ingestion.requests.get")
    def test_imports_normalized_records_to_java_api(self, get, source_request, post):
        source = {
            "id": "source-1",
            "nazwa": "Źródło testowe",
            "adresEndpointu": "https://example.test/api",
            "metoda": "GET",
            "parametryJson": json.dumps({
                "itemsPath": "items",
                "fields": {
                    "zewnetrzneId": "id",
                    "tytul": "title",
                    "opis": "description",
                    "kategoria": "category",
                    "lokalizacja": "location",
                    "terminSkladaniaOfert": "deadline",
                },
            }),
        }
        source_response = Mock()
        source_response.json.return_value = [source]
        get.return_value = source_response
        source_request.return_value = {"items": [{
            "id": "tender-1",
            "title": "Usługi IT",
            "description": "Opis",
            "category": "IT",
            "location": "Warszawa",
            "deadline": "2026-10-28T15:00:00",
        }]}
        post.return_value = Mock()
        with patch.object(ingestion, "API_KEY", "worker-key"):
            ingestion.poll_sources()

        self.assertEqual(post.call_count, 1)
        self.assertEqual(
            post.call_args.kwargs["json"],
            {
                "zrodloId": "source-1",
                "zewnetrzneId": "tender-1",
                "tytul": "Usługi IT",
                "opis": "Opis",
                "kategoria": "IT",
                "lokalizacja": "Warszawa",
                "terminSkladaniaOfert": "2026-10-28T15:00:00",
                "adresZrodla": None,
            },
        )
        self.assertEqual(post.call_args.kwargs["headers"]["X-Ingestion-Key"], "worker-key")


if __name__ == "__main__":
    unittest.main()
