package com.example.przetargapp.przetarg;

import java.time.LocalDateTime;
import java.util.UUID;

public record ZrodloPrzetarguResponse(
        UUID id,
        String nazwa,
        String adresEndpointu,
        String metoda,
        String parametryJson,
        boolean aktywne,
        LocalDateTime dataUtworzenia
) {
}
