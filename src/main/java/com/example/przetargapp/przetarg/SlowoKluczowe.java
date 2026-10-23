package com.example.przetargapp.przetarg;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "slowa_kluczowe", uniqueConstraints = @UniqueConstraint(
        name = "uk_slowa_kluczowe_fraza_typ", columnNames = {"fraza", "typ"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlowoKluczowe {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String fraza;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TypSlowaKluczowego typ;

    @Column(length = 100)
    private String kategoria;

    @Column(nullable = false)
    @Builder.Default
    private int punkty = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean aktywne = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataUtworzenia;

    @ManyToMany(mappedBy = "dopasowaneSlowa")
    @Builder.Default
    private Set<Przetarg> przetargi = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        dataUtworzenia = LocalDateTime.now();
    }
}
