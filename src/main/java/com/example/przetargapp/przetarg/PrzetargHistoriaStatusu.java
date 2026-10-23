package com.example.przetargapp.przetarg;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "historia_statusow_przetargow")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrzetargHistoriaStatusu {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "przetarg_id", nullable = false)
    private Przetarg przetarg;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private StatusOperacyjny poprzedniStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StatusOperacyjny nowyStatus;

    @Column(length = 160)
    private String zmienilUzytkownik;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataZmiany;

    @PrePersist
    protected void onCreate() {
        dataZmiany = LocalDateTime.now();
    }
}
