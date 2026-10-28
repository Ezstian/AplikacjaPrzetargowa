CREATE TABLE uzytkownicy (
    id UUID PRIMARY KEY,
    imie VARCHAR(255) NOT NULL,
    nazwisko VARCHAR(255) NOT NULL,
    rola VARCHAR(255) NOT NULL,
    telefon VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    haslo VARCHAR(255) NOT NULL,
    data_utworzenia TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE zrodla_przetargow (
    id UUID PRIMARY KEY,
    nazwa VARCHAR(120) NOT NULL UNIQUE,
    adres_endpointu VARCHAR(2048) NOT NULL,
    metoda VARCHAR(10) NOT NULL,
    parametry_json TEXT,
    aktywne BOOLEAN NOT NULL,
    data_utworzenia TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE slowa_kluczowe (
    id UUID PRIMARY KEY,
    fraza VARCHAR(200) NOT NULL,
    typ VARCHAR(24) NOT NULL,
    kategoria VARCHAR(100),
    punkty INTEGER NOT NULL,
    aktywne BOOLEAN NOT NULL,
    data_utworzenia TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_slowa_kluczowe_fraza_typ UNIQUE (fraza, typ)
);

CREATE TABLE przetargi (
    id UUID PRIMARY KEY,
    tytul VARCHAR(160) NOT NULL,
    opis TEXT NOT NULL,
    kategoria VARCHAR(100) NOT NULL,
    lokalizacja VARCHAR(160) NOT NULL,
    termin_skladania_ofert TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    status_operacyjny VARCHAR(24),
    punktacja INTEGER NOT NULL,
    status_ustawiony_recznie BOOLEAN NOT NULL,
    zrodlo_id UUID,
    zewnetrzne_id VARCHAR(200),
    adres_zrodla VARCHAR(2048),
    organizator_id UUID,
    data_utworzenia TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_przetarg_zrodlo_zewnetrzne UNIQUE (zrodlo_id, zewnetrzne_id),
    CONSTRAINT fk_przetargi_zrodlo FOREIGN KEY (zrodlo_id) REFERENCES zrodla_przetargow (id),
    CONSTRAINT fk_przetargi_organizator FOREIGN KEY (organizator_id) REFERENCES uzytkownicy (id)
);

CREATE TABLE przetargi_dopasowane_slowa (
    przetarg_id UUID NOT NULL,
    slowo_kluczowe_id UUID NOT NULL,
    PRIMARY KEY (przetarg_id, slowo_kluczowe_id),
    CONSTRAINT fk_dopasowane_slowa_przetarg FOREIGN KEY (przetarg_id) REFERENCES przetargi (id),
    CONSTRAINT fk_dopasowane_slowa_slowa FOREIGN KEY (slowo_kluczowe_id) REFERENCES slowa_kluczowe (id)
);

CREATE TABLE oferty (
    id UUID PRIMARY KEY,
    przetarg_id UUID NOT NULL,
    wykonawca_id UUID NOT NULL,
    cena NUMERIC(14, 2) NOT NULL,
    opis TEXT NOT NULL,
    data_zlozenia TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_oferty_przetarg_wykonawca UNIQUE (przetarg_id, wykonawca_id),
    CONSTRAINT fk_oferty_przetarg FOREIGN KEY (przetarg_id) REFERENCES przetargi (id),
    CONSTRAINT fk_oferty_wykonawca FOREIGN KEY (wykonawca_id) REFERENCES uzytkownicy (id)
);

CREATE TABLE historia_statusow_przetargow (
    id UUID PRIMARY KEY,
    przetarg_id UUID NOT NULL,
    poprzedni_status VARCHAR(24),
    nowy_status VARCHAR(24) NOT NULL,
    zmienil_uzytkownik VARCHAR(160),
    data_zmiany TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_historia_statusow_przetarg FOREIGN KEY (przetarg_id) REFERENCES przetargi (id)
);

CREATE INDEX idx_przetargi_status_operacyjny ON przetargi (status_operacyjny);
CREATE INDEX idx_przetargi_termin_skladania ON przetargi (termin_skladania_ofert);
CREATE INDEX idx_historia_statusow_przetarg_data ON historia_statusow_przetargow (przetarg_id, data_zmiany);
