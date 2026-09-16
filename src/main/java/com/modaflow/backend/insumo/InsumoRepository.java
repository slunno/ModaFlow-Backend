package com.modaflow.backend.insumo;

import com.modaflow.backend.marca.Marca;

import com.modaflow.backend.insumo.Insumo;
import com.modaflow.backend.insumo.TipoInsumoEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * REPOSITÓRIO: InsumoRepository
 * DESCRIÇÃO: Operações de banco de dados para Insumos (Tecidos e Aviamentos), filtrados por Marca e Tipo.
 */
@Repository
public interface InsumoRepository extends JpaRepository<Insumo, Long> {
    Page<Insumo> findByMarcaIdAndTipo(Long marcaId, TipoInsumoEnum tipo, Pageable pageable);
}
