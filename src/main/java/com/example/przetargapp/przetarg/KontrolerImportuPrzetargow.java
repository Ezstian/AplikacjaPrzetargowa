package com.example.przetargapp.przetarg;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/tenders")
@RequiredArgsConstructor
public class KontrolerImportuPrzetargow {
    private final PrzetargService przetargService;
    private final KluczImportuService kluczImportuService;

    @PostMapping
    public ResponseEntity<WynikImportuResponse> importuj(
            @RequestHeader(name = "X-Ingestion-Key", required = false) String providedKey,
            @Valid @RequestBody ImportPrzetarguRequest request) {
        kluczImportuService.sprawdz(providedKey);
        WynikImportuResponse result = przetargService.importuj(request);
        return ResponseEntity.status(result.nowy() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}
