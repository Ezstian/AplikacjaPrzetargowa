package com.example.przetargapp.przetarg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "powiadomienia_przetargow")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowiadomieniePrzetargu {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "przetarg_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_powiadomienie_przetarg"))
    private Przetarg przetarg;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String wiadomosc;

    @Column(nullable = false)
    private int liczbaProb;

    @Column(nullable = false)
    private LocalDateTime nastepnaProba;

    private LocalDateTime wyslanoAt;

    @Column(length = 1000)
    private String ostatniBlad;

    @Column(nullable = false, updatable = false)
    private LocalDateTime utworzonoAt;

    @jakarta.persistence.PrePersist
    protected void przedZapisem() {
        utworzonoAt = LocalDateTime.now();
        if (nastepnaProba == null) {
            nastepnaProba = utworzonoAt;
        }
    }
}
