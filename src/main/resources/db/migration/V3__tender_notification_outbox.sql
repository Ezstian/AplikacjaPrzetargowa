CREATE TABLE powiadomienia_przetargow (
    id UUID PRIMARY KEY,
    przetarg_id UUID NOT NULL UNIQUE,
    wiadomosc TEXT NOT NULL,
    liczba_prob INTEGER NOT NULL,
    nastepna_proba TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    wyslano_at TIMESTAMP WITHOUT TIME ZONE,
    ostatni_blad VARCHAR(1000),
    utworzono_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_powiadomienie_przetarg
        FOREIGN KEY (przetarg_id) REFERENCES przetargi (id) ON DELETE CASCADE
);

CREATE INDEX idx_powiadomienia_oczekujace
    ON powiadomienia_przetargow (nastepna_proba)
    WHERE wyslano_at IS NULL;
