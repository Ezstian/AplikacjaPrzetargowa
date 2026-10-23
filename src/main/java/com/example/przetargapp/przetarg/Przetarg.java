package com.example.przetargapp.przetarg;

import com.example.przetargapp.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "przetargi", uniqueConstraints = {
        @UniqueConstraint(name = "uk_przetarg_zrodlo_zewnetrzne", columnNames = {"zrodlo_id", "zewnetrzne_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Przetarg {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String tytul;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String opis;

    @Column(nullable = false, length = 100)
    private String kategoria;

    @Column(nullable = false, length = 160)
    private String lokalizacja;

    @Column(nullable = false)
    private LocalDateTime terminSkladaniaOfert;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusPrzetargu status = StatusPrzetargu.OTWARTY;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    @Builder.Default
    private StatusOperacyjny statusOperacyjny = StatusOperacyjny.NOWY;

    @Column
    @Builder.Default
    private int punktacja = 0;

    @Column
    @Builder.Default
    private boolean statusUstawionyRecznie = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zrodlo_id")
    private ZrodloPrzetargu zrodlo;

    @Column(name = "zewnetrzne_id", length = 200)
    private String zewnetrzneId;

    @Column(length = 2048)
    private String adresZrodla;

    @ManyToMany
    @JoinTable(
            name = "przetargi_dopasowane_slowa",
            joinColumns = @JoinColumn(name = "przetarg_id"),
            inverseJoinColumns = @JoinColumn(name = "slowo_kluczowe_id")
    )
    @Builder.Default
    private Set<SlowoKluczowe> dopasowaneSlowa = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizator_id")
    private User organizator;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataUtworzenia;

    @PrePersist
    protected void onCreate() {
        dataUtworzenia = LocalDateTime.now();
    }
    
}
