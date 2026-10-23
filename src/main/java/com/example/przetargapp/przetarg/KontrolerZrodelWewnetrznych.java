package com.example.przetargapp.przetarg;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/sources")
@RequiredArgsConstructor
public class KontrolerZrodelWewnetrznych {
    private final ZrodloPrzetarguService zrodloService;
    private final KluczImportuService kluczImportuService;

    @GetMapping
    public List<ZrodloPrzetarguResponse> listaAktywnych(
            @RequestHeader(name = "X-Ingestion-Key", required = false) String providedKey) {
        kluczImportuService.sprawdz(providedKey);
        return zrodloService.listaAktywnych();
    }
}
