package com.example.przetargapp.przetarg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PowiadomieniePrzetarguRepository extends JpaRepository<PowiadomieniePrzetargu, UUID> {
    boolean existsByPrzetargId(UUID przetargId);

    List<PowiadomieniePrzetargu> findTop20ByWyslanoAtIsNullAndNastepnaProbaLessThanEqualOrderByUtworzonoAtAsc(
            LocalDateTime teraz);
}
