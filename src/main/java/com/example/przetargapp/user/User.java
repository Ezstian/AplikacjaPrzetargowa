package com.example.przetargapp.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "uzytkownicy")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String imie;

    @Column(nullable = false)
    private String nazwisko;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private Rola rola = Rola.USER;

    @Size(min = 9, max = 9)
    @Column(nullable = false, unique = true)
    private String telefon;

    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String haslo;

    @Column(name = "data_utworzenia",updatable = false)
    private LocalDateTime dataUtworzenia;

    @PrePersist
    protected void onCreate(){
        this.dataUtworzenia = LocalDateTime.now();
    }

}
