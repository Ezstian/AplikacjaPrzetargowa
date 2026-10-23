package com.example.przetargapp.przetarg;

import java.util.List;
import java.util.UUID;

public record OpcjeFiltrowPrzetarguResponse(
        List<OpcjaZrodla> zrodla,
        List<OpcjaSlowa> slowa
) {
    public record OpcjaZrodla(UUID id, String nazwa) {
    }

    public record OpcjaSlowa(UUID id, String fraza, TypSlowaKluczowego typ) {
    }
}
