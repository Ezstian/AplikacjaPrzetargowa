package com.example.przetargapp.przetarg;
import com.example.przetargapp.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "oferty", uniqueConstraints = @UniqueConstraint(columnNames = {"przetarg_id", "wykonawca_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Oferta {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "przetarg_id", nullable = false)
    private Przetarg przetarg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wykonawca_id", nullable = false)
    private User wykonawca;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cena;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String opis;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataZlozenia;

    @PrePersist
    protected void onCreate() {
        dataZlozenia = LocalDateTime.now();
    }
}
