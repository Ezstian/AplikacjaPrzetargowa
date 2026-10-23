package com.example.przetargapp.przetarg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ZrodloPrzetarguRepository extends JpaRepository<ZrodloPrzetargu, UUID> {
    List<ZrodloPrzetargu> findAllByOrderByNazwaAsc();
    List<ZrodloPrzetargu> findAllByAktywneTrueOrderByNazwaAsc();
    boolean existsByNazwaIgnoreCase(String nazwa);
    boolean existsByNazwaIgnoreCaseAndIdNot(String nazwa, UUID id);
}
