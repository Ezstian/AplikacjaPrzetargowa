package com.example.przetargapp.przetarg;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/zrodla")
@RequiredArgsConstructor
public class ZrodloPrzetarguController {
    private final ZrodloPrzetarguService service;

    @GetMapping
    public List<ZrodloPrzetarguResponse> lista() {
        return service.lista();
    }

    @PostMapping
    public ResponseEntity<ZrodloPrzetarguResponse> utworz(@Valid @RequestBody ZrodloPrzetarguRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.utworz(request));
    }

    @PutMapping("/{id}")
    public ZrodloPrzetarguResponse aktualizuj(
            @PathVariable UUID id, @Valid @RequestBody ZrodloPrzetarguRequest request) {
        return service.aktualizuj(id, request);
    }

    @PostMapping("/{id}/test")
    public TestZrodlaResponse testuj(@PathVariable UUID id) {
        return service.testuj(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> usun(@PathVariable UUID id) {
        service.usun(id);
        return ResponseEntity.noContent().build();
    }
}
