package com.example.przetargapp.przetarg;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "zrodla_przetargow")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZrodloPrzetargu {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String nazwa;

    @Column(nullable = false, length = 2048)
    private String adresEndpointu;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String metoda = "GET";

    @Column(columnDefinition = "TEXT")
    private String parametryJson;

    @Column(nullable = false)
    @Builder.Default
    private boolean aktywne = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataUtworzenia;

    @PrePersist
    protected void onCreate() {
        dataUtworzenia = LocalDateTime.now();
    }
}
