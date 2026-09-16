package com.modaflow.backend.insumo;

import com.modaflow.backend.insumo.Caracteristica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITÓRIO: CaracteristicaRepository
 * DESCRIÇÃO: Operações de banco de dados para Características por marca.
 */
@Repository
public interface CaracteristicaRepository extends JpaRepository<Caracteristica, Long> {
    List<Caracteristica> findByMarcaId(Long marcaId);
}
