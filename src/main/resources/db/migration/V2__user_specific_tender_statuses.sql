ALTER TABLE historia_statusow_przetargow
    ADD COLUMN uzytkownik_id UUID;

ALTER TABLE historia_statusow_przetargow
    ADD CONSTRAINT fk_historia_statusow_uzytkownik
    FOREIGN KEY (uzytkownik_id) REFERENCES uzytkownicy (id) ON DELETE CASCADE;

CREATE TABLE statusy_przetargow_uzytkownikow (
    id UUID PRIMARY KEY,
    przetarg_id UUID NOT NULL,
    uzytkownik_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    data_zmiany TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_status_przetargu_uzytkownika UNIQUE (przetarg_id, uzytkownik_id),
    CONSTRAINT fk_status_przetargu_uzytkownika_przetarg
        FOREIGN KEY (przetarg_id) REFERENCES przetargi (id) ON DELETE CASCADE,
    CONSTRAINT fk_status_przetargu_uzytkownika_user
        FOREIGN KEY (uzytkownik_id) REFERENCES uzytkownicy (id) ON DELETE CASCADE
);

CREATE INDEX idx_statusy_przetargow_uzytkownik_user
    ON statusy_przetargow_uzytkownikow (uzytkownik_id);

WITH ostatnie_statusy_uzytkownikow AS (
    SELECT DISTINCT ON (historia.przetarg_id, historia.zmienil_uzytkownik)
        historia.id,
        historia.przetarg_id,
        uzytkownik.id AS uzytkownik_id,
        historia.nowy_status,
        historia.data_zmiany
    FROM historia_statusow_przetargow historia
    JOIN uzytkownicy uzytkownik ON uzytkownik.id::text = historia.zmienil_uzytkownik
    ORDER BY historia.przetarg_id, historia.zmienil_uzytkownik, historia.data_zmiany DESC
)
INSERT INTO statusy_przetargow_uzytkownikow (id, przetarg_id, uzytkownik_id, status, data_zmiany)
SELECT id, przetarg_id, uzytkownik_id, nowy_status, data_zmiany
FROM ostatnie_statusy_uzytkownikow;

UPDATE historia_statusow_przetargow historia
SET uzytkownik_id = uzytkownik.id
FROM uzytkownicy uzytkownik
WHERE uzytkownik.id::text = historia.zmienil_uzytkownik;

UPDATE przetargi
SET status_ustawiony_recznie = false;
