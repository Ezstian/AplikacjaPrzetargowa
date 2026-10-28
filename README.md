# Wewnętrzny Portal Monitoringu i Selekcji Przetargów

Portal gromadzi ogłoszenia z konfigurowanych źródeł API, usuwa duplikaty, ocenia ich dopasowanie do słów kluczowych i udostępnia zespołowi widoki operacyjne oraz statystyki. Aplikacja zawiera również konta organizatorów i wykonawców oraz obsługę publikowania postępowań i ofert.

## Architektura

- **Aplikacja Java 25 LTS**: Spring Boot 4, Spring MVC, Spring Security, Spring Data JPA i Hibernate.
- **Baza**: PostgreSQL 17; kontener utrzymuje dane w wolumenie `postgres_data`.
- **Ingestion Engine**: Python 3.13, Requests, APScheduler i Beautiful Soup. Pobiera konfigurację źródeł z Java API, pobiera ogłoszenia z JSON API, normalizuje je i przesyła do importu.
- **GUI**: statyczne strony HTML serwowane przez aplikację Java. Panel administracyjny obsługuje konta, źródła, słowa kluczowe i analitykę; panel operacyjny zawiera wyszukiwarkę, filtry i zmianę statusu.
- **Uruchamianie**: Docker Compose buduje i uruchamia PostgreSQL, aplikację Java oraz harmonogram Python.

## Uruchomienie lokalne

Wymagany jest Docker z Docker Compose. Przygotuj lokalny plik `.env` na podstawie przykładu i ustaw własne dane:

```powershell
Copy-Item .env.example .env
notepad .env
```

Zmień co najmniej `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_PHONE` i `INGESTION_API_KEY`. Hasło administratora musi mieć co najmniej 8 znaków, a telefon dokładnie 9 cyfr. Ustaw długi, losowy klucz importu; ta sama wartość jest przekazywana aplikacji Java i workerowi Python. Plik `.env` jest ignorowany przez Git — nie zapisuj w nim przykładowych ani prawdziwych haseł w repozytorium.

Uruchom cały stos jednym poleceniem:

```powershell
docker compose up --build
```

Portal będzie dostępny pod adresem <http://localhost:8080>. Porty Java i PostgreSQL są domyślnie przypięte do `127.0.0.1`; pierwsze budowanie pobiera obrazy i zależności, więc trwa dłużej. Worker czeka na gotowość API Java, wykonuje pierwszy przebieg pobierania i następnie uruchamia harmonogram. Domyślny interwał to 3600 sekund; można go zmienić przez `INGESTION_INTERVAL_SECONDS` (minimum 60 sekund). Stan PostgreSQL pozostaje w wolumenie `postgres_data`.

Zatrzymanie: `Ctrl+C` lub `docker compose down`. Polecenie `docker compose down -v` usuwa również trwały wolumen bazy i wszystkie zapisane w nim dane.

Port PostgreSQL wewnątrz sieci Compose pozostaje `5432`; domyślnie na hoście baza jest dostępna pod `127.0.0.1:5433`, aby uniknąć konfliktu z lokalnym PostgreSQL. Zmień `POSTGRES_PORT` w `.env`, jeśli port 5433 też jest zajęty. Jeśli port aplikacji 8080 jest zajęty, ustaw `APP_PORT=8081`. Dla wdrożenia za HTTPS ustaw `SESSION_COOKIE_SECURE=true` i skonfiguruj TLS na reverse proxy.

### Konto administratora

Przy pierwszym uruchomieniu `ADMIN_EMAIL`, `ADMIN_PASSWORD` i `ADMIN_PHONE` tworzą konto administratora. Hasło jest zapisywane jako hash BCrypt. Jeśli konto z tym adresem już istnieje i ma rolę administratora, start nie zmienia jego hasła. Zmiana `ADMIN_PASSWORD` w `.env` nie resetuje istniejącego konta — administrator może ustawić nowe hasło dla kont z panelu. Jeśli adres jest przypisany do konta bez roli administratora albo numer telefonu jest zajęty, aplikacja przerwie start z czytelnym błędem.

Logowanie: <http://localhost:8080/Logowanie.html>. Brak wbudowanego konta i domyślnego hasła. Użytkownik może założyć konto wykonawcy lub organizatora przez formularz rejestracji; nowych administratorów tworzy się wyłącznie przez konfigurację środowiska. Panel administratora zarządza źródłami, regułami i statystykami, a osobna strona `/UzytkownicyAdministratora.html` służy do tworzenia kont wykonawców i organizatorów, zmiany ról, resetu haseł i usuwania kont bez powiązanych ofert lub przetargów.

## Źródła i automatyczny import

Po zalogowaniu jako administrator otwórz **Panel administratora → Źródła i endpointy pobierania**:

1. Wprowadź nazwę, URL publicznego endpointu zwracającego JSON oraz metodę `GET` lub `POST`.
2. Skonfiguruj obiekt JSON, który określa parametry żądania i mapowanie odpowiedzi.
3. Zapisz źródło, a następnie użyj przycisku **Testuj**. Test sprawdza połączenie, kod HTTP i liczbę elementów pod `itemsPath`.
4. Pozostaw źródło aktywne, aby worker uwzględniał je przy kolejnych przebiegach.

Przykładowa konfiguracja dla odpowiedzi `{"data":{"items":[...]}}`:

```json
{
  "itemsPath": "data.items",
  "query": {
    "limit": 100
  },
  "headers": {
    "Authorization": "Bearer ${SOURCE_API_TOKEN}"
  },
  "fields": {
    "zewnetrzneId": "id",
    "tytul": "title",
    "opis": "description",
    "kategoria": "category",
    "lokalizacja": "location",
    "terminSkladaniaOfert": "deadline",
    "adresZrodla": "url"
  }
}
```

`itemsPath` wskazuje listę rekordów ścieżką z kropkami; pusty łańcuch oznacza, że cała odpowiedź jest tablicą. `query` dodaje parametry URL dla obu metod, `headers` ustawia nagłówki, a przy `POST` pole `body` określa obiekt wysyłany jako JSON. Wartości nagłówków mogą używać `${NAZWA_ZMIENNEJ}`; `SOURCE_API_TOKEN` z `.env` jest przekazywany do obu kontenerów, a dodatkowe zmienne można dopisać do ich sekcji `environment` w `compose.yaml`. Nie umieszczaj sekretów dostawcy API w publicznych plikach ani w repozytorium.

Mapowania `fields` wskazują ścieżki w rekordzie źródłowym do pól wymaganych przez portal. Wymagane są: zewnętrzny identyfikator, tytuł, opis, kategoria, lokalizacja i termin składania ofert; `adresZrodla` jest opcjonalny. Termin musi być datą ISO-8601. Ingestion Engine usuwa znaczniki HTML i normalizuje białe znaki w tekstach. Niepoprawny rekord jest pomijany z ostrzeżeniem w logach, pozostałe rekordy źródła nadal są przetwarzane.

Worker pobiera tylko aktywne źródła. Dla każdego ogłoszenia wysyła żądanie do Java API. Identyfikator ogłoszenia w ramach źródła jest kluczem deduplikacji: ponowny import aktualizuje istniejący rekord zamiast tworzyć duplikat. Projekt zawiera adapter dla konfigurowalnych API JSON; konkretne API może wymagać własnego mapowania pól, parametrów i nagłówków. Harmonogram nie obsługuje obecnie paginacji API.

### Powiadomienia o nowych przetargach

Worker wykonuje pierwszy przebieg po uruchomieniu, a następnie pobiera źródła zgodnie z `INGESTION_INTERVAL_SECONDS`. Każdy rekord przekazuje do Java API; Java zapisuje nowy przetarg, klasyfikuje go aktywnymi regułami i umieszcza powiadomienie w kolejce tylko wtedy, gdy jest nowy i ma automatyczny status `INTERESUJACY`. Ponowny import tego samego identyfikatora nie tworzy kolejnego powiadomienia.

Aby wysyłać powiadomienia na komunikator, ustaw `TENDER_NOTIFICATION_WEBHOOK_URL` w `.env` na adres przychodzącego webhooka Slack lub własnego endpointu zgodnego z JSON-em `{ "text": "..." }`. Java sprawdza kolejkę co 30 sekund, wysyła tytuł, kategorię, lokalizację, termin oraz — jeśli istnieje — link do ogłoszenia. Przy błędzie zachowuje powiadomienie w PostgreSQL i ponawia próbę z rosnącymi odstępami (maksymalnie 8 prób); ostatni błąd znajduje się w logach aplikacji. Po ośmiu nieudanych próbach wpis pozostaje w bazie do ręcznej diagnostyki, ale nie jest automatycznie ponawiany. Gdy webhook jest pusty, wysyłka jest wyłączona, a import i harmonogram działają normalnie.

Webhook jest sekretem: nie zapisuj jego adresu w repozytorium i preferuj adres HTTPS. Po zmianie `.env` zastosuj ustawienie poleceniem `docker compose up -d --force-recreate app`.

## Reguły klasyfikacji i widok operacyjny

Administrator dodaje w panelu reguły `POSZUKIWANE` i `WYKLUCZAJACE`, kategorię/tag oraz wagę punktową. Reguły są dopasowywane bez rozróżniania wielkości liter do tytułu i opisu:

- dopasowane aktywne frazy poszukiwane sumują punkty;
- dowolna aktywna fraza wykluczająca powoduje status `ODRZUCONY`;
- brak dopasowanych fraz poszukiwanych lub wynik 0 punktów również oznacza `ODRZUCONY`;
- dodatnia punktacja bez frazy wykluczającej daje `INTERESUJACY`;
- `NOWY` jest statusem początkowym przed klasyfikacją lub ręcznym rozstrzygnięciem.

Zmiana reguł ponownie klasyfikuje zapisane ogłoszenia. Status wynikający z automatycznej klasyfikacji jest wspólną wartością bazową; ręczny status użytkownika jest zapisywany osobno dla pary przetarg–użytkownik i nie zmienia widoku pozostałych osób. Lista, szczegóły i filtr statusu pokazują zalogowanemu użytkownikowi jego własny status, a gdy go nie ustawił — status bazowy. W tabeli zmiana statusu zapisuje się natychmiast po wyborze nowej wartości z listy (jedno działanie, bez dodatkowego przycisku zapisu). Historia statusów pokazuje automatyczne zdarzenia oraz zmiany bieżącego użytkownika. Przy migracji Flyway V2 dotychczasowe ręczne zmiany rozdzielane są na podstawie zapisanych identyfikatorów użytkowników. Statystyki lejka i skuteczność fraz pokazują globalną automatyczną klasyfikację, nie osobiste statusy. Panel przetargów filtruje także po dacie utworzenia (`od`, `doDaty`), punktacji (`minPunktacja`, `maxPunktacja`), źródle (`zrodloId`), dopasowanym słowie (`slowoId`) i tekście (`query`, szukanie w tytule, opisie, kategorii i lokalizacji).

Status operacyjny (`NOWY`, `INTERESUJACY`, `ODRZUCONY`) jest oddzielony od statusu postępowania (`OTWARTY`, `ZAMKNIETY` lub wyliczonego `ZAKONCZONY`).

## Konta i bezpieczeństwo

- Hasła użytkowników są kodowane przez BCrypt; odpowiedzi API nie zawierają haseł.
- Uwierzytelnienie korzysta z sesji serwerowej. Rola użytkownika jest odczytywana z bazy przy obsłudze żądań. Cookie sesji ma `SameSite=Strict`; w środowisku produkcyjnym używaj HTTPS i włącz `SESSION_COOKIE_SECURE=true`.
- Żądania zmieniające stan wymagają tokenu CSRF (`GET /api/csrf`, następnie nagłówek `X-XSRF-TOKEN`). Formularze przeglądarkowe pobierają i wysyłają go automatycznie.
- Endpointy `/api/zrodla/**`, `/api/slowa-kluczowe/**`, `/api/analytics/**` i `/api/users/admin/**` wymagają roli administratora. Pozostałe prywatne endpointy wymagają zalogowania; dostęp do profilu ograniczono do właściciela lub administratora.
- API wewnętrzne modułu pobierającego (`/api/internal/**`) wymaga nagłówka `X-Ingestion-Key`, porównywanego w sposób stałoczasowy. Bez `INGESTION_API_KEY` import jest wyłączony. Udostępniaj port aplikacji tylko zaufanym użytkownikom i nie wystawiaj endpointów wewnętrznych bezpośrednio do Internetu.
- Baza PostgreSQL jest przeznaczona do wewnętrznej sieci aplikacji; zmień hasło i ogranicz dostęp sieciowy przed wdrożeniem produkcyjnym.

## API

| Endpoint | Dostęp | Działanie |
|---|---|---|
| `GET /api/csrf` | publiczny | Pobranie tokenu CSRF dla przeglądarki |
| `POST /api/users/rejestracja`, `POST /api/users/login` | publiczny + CSRF | Rejestracja i logowanie |
| `POST /api/users/logout` | zalogowany + CSRF | Zakończenie sesji |
| `GET /api/users/{id}` | właściciel lub administrator | Profil użytkownika |
| `/api/users/admin/**` | administrator | Konta, role, reset hasła i usuwanie |
| `GET/POST/PUT/DELETE /api/zrodla` | administrator | Konfiguracja źródeł |
| `POST /api/zrodla/{id}/test` | administrator | Test HTTP i odpowiedzi JSON źródła |
| `GET/POST/PUT/DELETE /api/slowa-kluczowe` | administrator | Słownik reguł klasyfikacji |
| `GET /api/analytics/przetargi?od=...&doDaty=...` | administrator | Statystyki i skuteczność słów |
| `GET /api/internal/sources` | klucz importu | Lista aktywnych źródeł dla workera |
| `POST /api/internal/tenders` | klucz importu | Import lub aktualizacja ogłoszenia |
| `GET /api/przetargi` | zalogowany | Lista przetargów i filtrów wymienionych wyżej |
| `GET /api/przetargi/filtry` | zalogowany | Opcje aktywnych źródeł i fraz |
| `PATCH /api/przetargi/{id}/status` | zalogowany | Ręczna zmiana statusu i wpis w historii |
| `GET /api/przetargi/{id}/historia` | zalogowany | Historia zmian statusu |
| `POST /api/przetargi`, `POST /api/przetargi/{id}/oferty` | zalogowany; uprawnienia roli | Publikacja postępowania i składanie oferty |

## Testy i diagnostyka

Testy backendu (wymagany JDK 25 lub nowszy):

```powershell
.\mvnw.cmd clean test
```

Testy workera Python:

```powershell
python -m pip install -r ingestion\requirements.txt
python -m unittest discover -s ingestion -p "test_*.py"
```

Logi kontenerów:

```powershell
docker compose logs -f app ingestion db
```

Schemat PostgreSQL jest utrzymywany przez Flyway. Nowa baza uruchamia migrację `V1__create_tender_portal_schema.sql`, a Hibernate sprawdza zgodność encji ze schematem (`ddl-auto=validate`) zamiast modyfikować go przy starcie. Dla istniejącej, niepustej bazy włączono baseline na wersji 1: Flyway nie wykona w niej ponownie migracji początkowej, a Hibernate zweryfikuje tabele. Przed podłączeniem starszej bazy wykonaj kopię zapasową; jeśli jej schemat różni się od encji, aplikacja zatrzyma start i trzeba przygotować osobną migrację danych/schematu.
