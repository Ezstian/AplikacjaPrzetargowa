package com.example.przetargapp.przetarg;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record ImportPrzetarguRequest(
        @NotNull UUID zrodloId,
        @NotBlank @Size(max = 200) String zewnetrzneId,
        @NotBlank @Size(max = 160) String tytul,
        @NotBlank @Size(max = 20000) String opis,
        @NotBlank @Size(max = 100) String kategoria,
        @NotBlank @Size(max = 160) String lokalizacja,
        @NotNull LocalDateTime terminSkladaniaOfert,
        @Size(max = 2048) String adresZrodla
) {
}
