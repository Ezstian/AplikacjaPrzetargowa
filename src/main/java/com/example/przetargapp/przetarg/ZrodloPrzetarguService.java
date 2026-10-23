package com.example.przetargapp.przetarg;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZrodloPrzetarguService {
    private final ZrodloPrzetarguRepository zrodloRepository;
    private final PrzetargRepository przetargRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<ZrodloPrzetarguResponse> lista() {
        return zrodloRepository.findAllByOrderByNazwaAsc().stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ZrodloPrzetarguResponse> listaAktywnych() {
        return zrodloRepository.findAllByOrderByNazwaAsc().stream()
                .filter(ZrodloPrzetargu::isAktywne)
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ZrodloPrzetarguResponse utworz(ZrodloPrzetarguRequest request) {
        String nazwa = request.nazwa().trim();
        if (zrodloRepository.existsByNazwaIgnoreCase(nazwa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Źródło o tej nazwie już istnieje");
        }
        ZrodloPrzetargu zrodlo = ZrodloPrzetargu.builder().build();
        aktualizuj(zrodlo, request, true);
        return mapToResponse(zrodloRepository.save(zrodlo));
    }

    @Transactional
    public ZrodloPrzetarguResponse aktualizuj(UUID id, ZrodloPrzetarguRequest request) {
        ZrodloPrzetargu zrodlo = znajdz(id);
        if (zrodloRepository.existsByNazwaIgnoreCaseAndIdNot(request.nazwa().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Źródło o tej nazwie już istnieje");
        }
        aktualizuj(zrodlo, request, false);
        return mapToResponse(zrodloRepository.save(zrodlo));
    }

    @Transactional
    public void usun(UUID id) {
        ZrodloPrzetargu zrodlo = znajdz(id);
        if (przetargRepository.existsByZrodloId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nie można usunąć źródła powiązanego z przetargami");
        }
        zrodloRepository.delete(zrodlo);
    }

    private void aktualizuj(ZrodloPrzetargu zrodlo, ZrodloPrzetarguRequest request, boolean nowe) {
        zrodlo.setNazwa(request.nazwa().trim());
        zrodlo.setAdresEndpointu(request.adresEndpointu().trim());
        String metoda = request.metoda().trim().toUpperCase();
        if (!metoda.equals("GET") && !metoda.equals("POST")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dozwolone metody źródła to GET i POST");
        }
        zrodlo.setMetoda(metoda);
        if (request.parametryJson() != null && !request.parametryJson().isBlank()) {
            try {
                objectMapper.readTree(request.parametryJson());
            } catch (JsonProcessingException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parametry źródła muszą być poprawnym JSON-em");
            }
        }
        zrodlo.setParametryJson(request.parametryJson());
        zrodlo.setAktywne(request.aktywne() == null ? nowe || zrodlo.isAktywne() : request.aktywne());
    }

    private ZrodloPrzetargu znajdz(UUID id) {
        return zrodloRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono źródła"));
    }

    private ZrodloPrzetarguResponse mapToResponse(ZrodloPrzetargu zrodlo) {
        return new ZrodloPrzetarguResponse(
                zrodlo.getId(), zrodlo.getNazwa(), zrodlo.getAdresEndpointu(), zrodlo.getMetoda(),
                zrodlo.getParametryJson(), zrodlo.isAktywne(), zrodlo.getDataUtworzenia());
    }
}
