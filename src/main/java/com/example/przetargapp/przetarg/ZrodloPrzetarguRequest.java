package com.example.przetargapp.przetarg;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ZrodloPrzetarguRequest(
        @NotBlank @Size(max = 120) String nazwa,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://.+") String adresEndpointu,
        @NotBlank @Size(max = 10) String metoda,
        @Size(max = 10000) String parametryJson,
        Boolean aktywne
) {
}
