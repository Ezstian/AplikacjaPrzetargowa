package com.example.przetargapp.przetarg;

import java.util.UUID;

public record WynikImportuResponse(
        UUID id,
        boolean nowy,
        StatusOperacyjny statusOperacyjny,
        int punktacja
) {
}
