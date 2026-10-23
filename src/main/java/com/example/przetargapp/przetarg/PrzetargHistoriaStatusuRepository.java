package com.example.przetargapp.przetarg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrzetargHistoriaStatusuRepository extends JpaRepository<PrzetargHistoriaStatusu, UUID> {
    List<PrzetargHistoriaStatusu> findByPrzetargIdOrderByDataZmianyDesc(UUID przetargId);
}
