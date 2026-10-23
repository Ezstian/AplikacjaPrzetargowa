# Przetarg App

## Konfiguracja i uruchomienie

Projekt wymaga JDK 21. Po skonfigurowaniu bazy PostgreSQL można uruchomić aplikację poleceniem:

```powershell
.\mvnw.cmd spring-boot:run
```

Ustawienia bazy przekazuje się przez `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` i `DB_PASSWORD`.

Aby uruchomić aplikację i PostgreSQL w Docker Compose:

```powershell
docker compose up --build
```

Bazę można przeglądać w pgAdminie, łącząc się z hostem `localhost` i portem `5432`. Domyślnie baza nazywa się `przetarg_app_db`, a użytkownik `przetarg`; wartości można zmienić przez `DB_NAME`, `DB_USERNAME` i `DB_PASSWORD`. Jeśli port `5432` jest zajęty, ustaw `POSTGRES_PORT` na wolny port hosta.

Aby utworzyć pierwsze konto administratora w Dockerze, utwórz w katalogu projektu lokalny plik `.env` z własnymi danymi:

```dotenv
ADMIN_EMAIL=twoj-adres@example.com
ADMIN_PASSWORD=twoje-wlasne-haslo
ADMIN_PHONE=500600700
```

Hasło musi mieć co najmniej 8 znaków, a telefon dokładnie 9 cyfr i nie może być już przypisany do innego konta. Uruchom lub odtwórz kontener aplikacji poleceniem `docker compose up -d app`, a następnie zaloguj się adresem z `ADMIN_EMAIL` i ustawionym hasłem pod adresem `http://localhost:8080/Logowanie.html`. Plik `.env` jest ignorowany przez Git — nie wpisuj prawdziwych danych logowania do kodu ani nie wysyłaj ich innym osobom.

Nie ma wbudowanego konta ani domyślnego hasła. Jeśli konto administratora z podanym adresem już istnieje, aplikacja nie zmienia jego hasła; samo dopisanie go do `.env` nie resetuje istniejącego konta.

Import z modułu pobierającego wymaga ustawienia `INGESTION_API_KEY`. Klucz należy przekazywać w nagłówku `X-Ingestion-Key`; bez skonfigurowanego klucza endpointy wewnętrzne odmawiają działania.

### Port 8080 jest zajęty

Jeśli `docker ps` pokazuje kontener `przetarg-app-app-1`, to on już uruchamia tę aplikację. Nie uruchamiaj wtedy drugiej kopii przez `spring-boot:run`; otwórz `http://localhost:8080/Rejestracja.html`. Aby przebudować i uruchomić aplikację oraz bazę razem, użyj `docker compose up --build -d`.

Jeśli port 8080 zajmuje inna aplikacja, uruchom stos na porcie 8081:

```powershell
$env:APP_PORT = "8081"
docker compose up --build -d
```

Wtedy otwórz `http://localhost:8081/Rejestracja.html`. `APP_PORT` zmienia port na komputerze; aplikacja w kontenerze nadal nasłuchuje na 8080.

## API Java

- `POST /api/users/rejestracja`, `POST /api/users/login` — rejestracja i logowanie; pozostałe API wymaga sesji.
- `GET/POST/PUT/DELETE /api/zrodla` — administracja źródłami pobierania.
- `GET/POST/PUT/DELETE /api/slowa-kluczowe` — administracja frazami `POSZUKIWANE` i `WYKLUCZAJACE`.
- `GET /api/internal/sources` — aktywne konfiguracje źródeł dla modułu pobierającego.
- `POST /api/internal/tenders` — import lub aktualizacja ogłoszenia, deduplikowane przez identyfikator źródła i identyfikator zewnętrzny.
- `GET /api/przetargi` — wyszukiwanie; obsługuje `status`, `od`, `doDaty`, `minPunktacja`, `maxPunktacja`, `zrodloId`, `slowoId` i `query`.
- `GET /api/przetargi/filtry` — bezpieczne opcje źródeł i aktywnych słów do filtrowania.
- `PATCH /api/przetargi/{id}/status` — ręczna zmiana statusu przez zalogowanego użytkownika; historia zmian jest dostępna pod `/api/przetargi/{id}/historia`.
- `/PanelPrzetargow.html` — tabela przetargów podzielona na nowe, interesujące i odrzucone; filtry obejmują datę, status, punktację, źródło, słowo kluczowe i tekst. Zmiany statusu są zapisywane w historii.
- `GET /api/analytics/przetargi` — statystyki lejka i skuteczność słów kluczowych.
- `POST /api/users/admin` i `PUT /api/users/admin/{id}/haslo` — administracyjne tworzenie kont i reset hasła.
- `/PanelAdministratora.html` — panel administratora do wyszukiwania kont, tworzenia użytkowników, zmiany ról, resetowania haseł i usuwania kont.

Aktywne frazy poszukiwane dodają przypisaną punktację. Dopasowanie frazy wykluczającej ma pierwszeństwo; brak punktowanych dopasowań albo wykrycie frazy wykluczającej oznacza status `ODRZUCONY`, a dodatnia punktacja bez wykluczeń — `INTERESUJACY`. Ręcznie ustawiony status jest zachowywany podczas kolejnego przeliczenia. Status operacyjny jest oddzielony od statusu składania ofert (`OTWARTY`/`ZAMKNIETY`).

## Testy

```powershell
.\mvnw.cmd -Dtest=PrzetargAppApplicationTests test
```
