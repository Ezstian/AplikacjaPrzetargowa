package com.example.przetargapp.przetarg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "statusy_przetargow_uzytkownikow", uniqueConstraints = @UniqueConstraint(
        name = "uk_status_przetargu_uzytkownika", columnNames = {"przetarg_id", "uzytkownik_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrzetargStatusUzytkownika {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "przetarg_id", nullable = false)
    private UUID przetargId;

    @Column(name = "uzytkownik_id", nullable = false)
    private UUID uzytkownikId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StatusOperacyjny status;

    @Column(nullable = false)
    private LocalDateTime dataZmiany;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    protected void aktualizujDateZmiany() {
        dataZmiany = LocalDateTime.now();
    }
}
