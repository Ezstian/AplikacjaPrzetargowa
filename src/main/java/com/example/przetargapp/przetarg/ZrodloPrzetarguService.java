package com.example.przetargapp.przetarg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

@Service
public class ZrodloPrzetarguService {
    private static final Pattern ENVIRONMENT_VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private final ZrodloPrzetarguRepository zrodloRepository;
    private final PrzetargRepository przetargRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ZrodloPrzetarguService(
            ZrodloPrzetarguRepository zrodloRepository,
            PrzetargRepository przetargRepository) {
        this.zrodloRepository = zrodloRepository;
        this.przetargRepository = przetargRepository;
    }

    @Transactional(readOnly = true)
    public List<ZrodloPrzetarguResponse> lista() {
        return zrodloRepository.findAllByOrderByNazwaAsc().stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ZrodloPrzetarguResponse> listaAktywnych() {
        List<ZrodloPrzetarguResponse> aktywneZrodla = new ArrayList<>();
        for (ZrodloPrzetargu zrodlo : zrodloRepository.findAllByOrderByNazwaAsc()) {
            if (zrodlo.isAktywne()) {
                aktywneZrodla.add(mapToResponse(zrodlo));
            }
        }
        return aktywneZrodla;
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

    @Transactional(readOnly = true)
    public TestZrodlaResponse testuj(UUID id) {
        ZrodloPrzetargu zrodlo = znajdz(id);
        JsonNode config;
        try {
            config = objectMapper.readTree(
                    zrodlo.getParametryJson() == null || zrodlo.getParametryJson().isBlank()
                            ? "{}"
                            : zrodlo.getParametryJson());
            if (config == null || !config.isObject()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Konfiguracja źródła musi być obiektem JSON");
            }
            sprawdzKonfiguracje(config.toString());
            URI uri = zbudujUri(zrodlo, config);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json");
            JsonNode headers = config.path("headers");
            if (headers.isObject()) {
                headers.fields().forEachRemaining(entry -> request.header(
                        entry.getKey(), rozwinZmiennaSrodowiskowa(entry.getValue().asText())));
            }
            String method = zrodlo.getMetoda();
            if ("POST".equals(method)) {
                JsonNode body = config.path("body");
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                body.isMissingNode() ? "{}" : body.toString()));
            } else {
                request.GET();
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Źródło zwróciło kod HTTP " + response.statusCode());
            }
            JsonNode payload = objectMapper.readTree(response.body());
            if (payload == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Źródło zwróciło pustą odpowiedź");
            }
            String itemsPath = config.path("itemsPath").asText("");
            JsonNode items = odczytajSciezke(payload, itemsPath);
            int count = items.isArray() ? items.size() : payload.isArray() ? payload.size() : 0;
            return new TestZrodlaResponse(response.statusCode(), count, "Połączenie ze źródłem działa.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Test źródła został przerwany");
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Nie udało się połączyć ze źródłem lub odczytać JSON");
        }
    }

    private URI zbudujUri(ZrodloPrzetargu zrodlo, JsonNode config) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(zrodlo.getAdresEndpointu());
        JsonNode query = config.path("query");
        if (query.isObject()) {
            query.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    value.forEach(item -> builder.queryParam(entry.getKey(), item.asText()));
                } else if (value.isValueNode()) {
                    builder.queryParam(entry.getKey(), value.asText());
                }
            });
        }
        return builder.build().encode().toUri();
    }

    private JsonNode odczytajSciezke(JsonNode payload, String path) {
        JsonNode result = payload;
        if (!path.isBlank()) {
            for (String part : path.split("\\.")) {
                result = result.path(part);
            }
        }
        return result;
    }

    private String rozwinZmiennaSrodowiskowa(String value) {
        Matcher matcher = ENVIRONMENT_VARIABLE.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String secret = System.getenv(matcher.group(1));
            if (secret == null || secret.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Brak wymaganej zmiennej środowiskowej: " + matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(secret));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void aktualizuj(ZrodloPrzetargu zrodlo, ZrodloPrzetarguRequest request, boolean nowe) {
        zrodlo.setNazwa(request.nazwa().trim());
        zrodlo.setAdresEndpointu(request.adresEndpointu().trim());
        String metoda = request.metoda().trim().toUpperCase();
        if (!metoda.equals("GET") && !metoda.equals("POST")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dozwolone metody źródła to GET i POST");
        }
        zrodlo.setMetoda(metoda);
        sprawdzKonfiguracje(request.parametryJson());
        zrodlo.setParametryJson(request.parametryJson());
        Boolean aktywne = request.aktywne();
        zrodlo.setAktywne(aktywne == null
                ? nowe || zrodlo.isAktywne()
                : Boolean.TRUE.equals(aktywne));
    }

    private void sprawdzKonfiguracje(String json) {
        if (json == null || json.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Konfiguracja źródła jest wymagana");
        }
        JsonNode config;
        try {
            config = objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parametry źródła muszą być poprawnym JSON-em");
        }
        if (config == null || !config.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Konfiguracja źródła musi być obiektem JSON");
        }
        for (String property : List.of("query", "headers", "body", "fields")) {
            JsonNode value = config.path(property);
            if (!value.isMissingNode() && !value.isObject()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Pole " + property + " musi być obiektem JSON");
            }
        }
        JsonNode itemsPath = config.path("itemsPath");
        if (!itemsPath.isMissingNode() && !itemsPath.isTextual()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pole itemsPath musi być tekstem");
        }
        JsonNode fields = config.path("fields");
        for (String field : List.of(
                "zewnetrzneId", "tytul", "opis", "kategoria", "lokalizacja", "terminSkladaniaOfert")) {
            if (!fields.path(field).isTextual() || fields.path(field).asText().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Wymagane mapowanie fields." + field);
            }
        }
        JsonNode addressPath = fields.path("adresZrodla");
        if (!addressPath.isMissingNode() && !addressPath.isTextual()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mapowanie fields.adresZrodla musi być tekstem");
        }
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
