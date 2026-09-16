package com.modaflow.backend.time;

import com.modaflow.backend.time.Time;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITÓRIO: TimeRepository
 * DESCRIÇÃO: Operações de banco de dados para Times por marca.
 */
@Repository
public interface TimeRepository extends JpaRepository<Time, Long> {
    List<Time> findByMarcaId(Long marcaId);
}
