package com.example.przetargapp.przetarg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class KluczImportuService {
    @Value("${app.ingestion.api-key:}")
    private String ingestionApiKey;

    public void sprawdz(String providedKey) {
        if (ingestionApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Import danych nie został skonfigurowany");
        }
        if (providedKey == null || !MessageDigest.isEqual(
                ingestionApiKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy klucz importu");
        }
    }
}
