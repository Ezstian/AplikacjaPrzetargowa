package com.example.przetargapp.przetarg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PowiadomieniaPrzetargowService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PowiadomieniaPrzetargowService.class);
    private static final int MAX_PROB = 8;

    private final PowiadomieniePrzetarguRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final URI webhookUri;

    public PowiadomieniaPrzetargowService(
            PowiadomieniePrzetarguRepository repository,
            @Value("${app.notifications.webhook-url:}") String webhookUrl) {
        this.repository = repository;
        this.webhookUri = skonfigurujWebhook(webhookUrl);
        if (webhookUri == null) {
            LOGGER.info("Powiadomienia webhook są wyłączone; ustaw TENDER_NOTIFICATION_WEBHOOK_URL, aby je włączyć.");
        }
    }

    public void dodajNowyInteresujacy(Przetarg przetarg, boolean nowy) {
        if (!nowy
                || webhookUri == null
                || przetarg.getStatusOperacyjny() != StatusOperacyjny.INTERESUJACY
                || repository.existsByPrzetargId(przetarg.getId())) {
            return;
        }
        repository.save(PowiadomieniePrzetargu.builder()
                .przetarg(przetarg)
                .wiadomosc(utworzWiadomosc(przetarg))
                .liczbaProb(0)
                .nastepnaProba(LocalDateTime.now())
                .build());
    }

    @Scheduled(
            initialDelayString = "${app.notifications.initial-delay-ms:10000}",
            fixedDelayString = "${app.notifications.dispatch-interval-ms:30000}")
    public void wyslijOczekujace() {
        if (webhookUri == null) {
            return;
        }
        List<PowiadomieniePrzetargu> oczekujace = repository
                .findTop20ByWyslanoAtIsNullAndNastepnaProbaLessThanEqualOrderByUtworzonoAtAsc(
                        LocalDateTime.now());
        oczekujace.forEach(this::wyslij);
    }

    private void wyslij(PowiadomieniePrzetargu powiadomienie) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("text", powiadomienie.getWiadomosc()));
            HttpRequest request = HttpRequest.newBuilder(webhookUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                zaplanujPonowienie(powiadomienie, "Webhook zwrócił HTTP " + response.statusCode());
                return;
            }
            powiadomienie.setWyslanoAt(LocalDateTime.now());
            powiadomienie.setOstatniBlad(null);
            repository.save(powiadomienie);
            LOGGER.info("Wysłano powiadomienie dla przetargu {}", powiadomienie.getPrzetarg().getId());
        } catch (IOException exception) {
            zaplanujPonowienie(powiadomienie, "Błąd połączenia z webhookiem: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            zaplanujPonowienie(powiadomienie, "Wysyłanie powiadomienia zostało przerwane.");
        }
    }

    private void zaplanujPonowienie(PowiadomieniePrzetargu powiadomienie, String blad) {
        int liczbaProb = powiadomienie.getLiczbaProb() + 1;
        powiadomienie.setLiczbaProb(liczbaProb);
        powiadomienie.setOstatniBlad(blad == null || blad.isBlank() ? "Nieznany błąd webhooka" : blad);
        if (liczbaProb >= MAX_PROB) {
            powiadomienie.setNastepnaProba(LocalDateTime.of(9999, 12, 31, 0, 0));
        } else {
            long opoznienie = Math.min(3600, 30L << Math.min(liczbaProb - 1, 7));
            powiadomienie.setNastepnaProba(LocalDateTime.now().plusSeconds(opoznienie));
        }
        repository.save(powiadomienie);
        LOGGER.warn(
                "Nie udało się wysłać powiadomienia dla przetargu {} (próba {} z {}): {}",
                powiadomienie.getPrzetarg().getId(), liczbaProb, MAX_PROB, powiadomienie.getOstatniBlad());
    }

    private String utworzWiadomosc(Przetarg przetarg) {
        StringBuilder message = new StringBuilder("Nowy interesujący przetarg: ")
                .append(przetarg.getTytul())
                .append("\nKategoria: ").append(przetarg.getKategoria())
                .append("\nLokalizacja: ").append(przetarg.getLokalizacja())
                .append("\nTermin składania ofert: ").append(przetarg.getTerminSkladaniaOfert());
        if (przetarg.getAdresZrodla() != null && !przetarg.getAdresZrodla().isBlank()) {
            message.append("\nOgłoszenie: ").append(przetarg.getAdresZrodla());
        }
        return message.toString();
    }

    private URI skonfigurujWebhook(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(webhookUrl.trim());
            if (uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("TENDER_NOTIFICATION_WEBHOOK_URL musi być poprawnym adresem HTTP(S).");
        }
    }
}
