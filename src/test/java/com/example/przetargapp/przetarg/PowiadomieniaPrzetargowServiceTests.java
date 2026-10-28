package com.example.przetargapp.przetarg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PowiadomieniaPrzetargowServiceTests {
    @Test
    void queuesOnlyNewInterestingTendersWhenWebhookIsConfigured() {
        PowiadomieniePrzetarguRepository repository = mock(PowiadomieniePrzetarguRepository.class);
        Przetarg tender = tender(StatusOperacyjny.INTERESUJACY);
        when(repository.existsByPrzetargId(tender.getId())).thenReturn(false);
        PowiadomieniaPrzetargowService service =
                new PowiadomieniaPrzetargowService(repository, "https://hooks.example.test/webhook");

        service.dodajNowyInteresujacy(tender, true);
        service.dodajNowyInteresujacy(tender, false);

        var saved = org.mockito.ArgumentCaptor.forClass(PowiadomieniePrzetargu.class);
        verify(repository).save(saved.capture());
        assertEquals(tender.getId(), saved.getValue().getPrzetarg().getId());
        assertTrue(saved.getValue().getWiadomosc().contains("Nowy interesujący przetarg"));
        assertTrue(saved.getValue().getWiadomosc().contains(tender.getTytul()));
    }

    @Test
    void sendsPendingWebhookAndSchedulesRetryAfterFailure() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = requests.incrementAndGet() == 1 ? 204 : 503;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            PowiadomieniePrzetarguRepository repository = mock(PowiadomieniePrzetarguRepository.class);
            PowiadomieniePrzetargu sent = notification();
            PowiadomieniePrzetargu retry = notification();
            LocalDateTime retryBefore = LocalDateTime.now();
            when(repository.findTop20ByWyslanoAtIsNullAndNastepnaProbaLessThanEqualOrderByUtworzonoAtAsc(any()))
                    .thenReturn(List.of(sent, retry));
            String webhook = "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
            PowiadomieniaPrzetargowService service = new PowiadomieniaPrzetargowService(repository, webhook);

            service.wyslijOczekujace();

            JsonNode payload = new ObjectMapper().readTree(requestBody.get());
            assertTrue(payload.path("text").asText().contains(sent.getWiadomosc()));
            assertNotNull(sent.getWyslanoAt());
            assertEquals(1, retry.getLiczbaProb());
            assertTrue(retry.getNastepnaProba().isAfter(retryBefore));
            assertFalse(retry.getOstatniBlad().isBlank());
            verify(repository, org.mockito.Mockito.times(2)).save(any(PowiadomieniePrzetargu.class));
        } finally {
            server.stop(0);
        }
    }

    private Przetarg tender(StatusOperacyjny status) {
        return Przetarg.builder()
                .id(UUID.randomUUID())
                .tytul("Dostawa usług IT")
                .kategoria("IT")
                .lokalizacja("Warszawa")
                .terminSkladaniaOfert(LocalDateTime.now().plusDays(10))
                .statusOperacyjny(status)
                .build();
    }

    private PowiadomieniePrzetargu notification() {
        return PowiadomieniePrzetargu.builder()
                .id(UUID.randomUUID())
                .przetarg(tender(StatusOperacyjny.INTERESUJACY))
                .wiadomosc("Nowy interesujący przetarg: test")
                .liczbaProb(0)
                .nastepnaProba(LocalDateTime.now())
                .utworzonoAt(LocalDateTime.now())
                .build();
    }
}
