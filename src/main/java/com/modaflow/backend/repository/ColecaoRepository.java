package com.modaflow.backend.repository;

import com.modaflow.backend.entity.Colecao;
import com.modaflow.backend.enums.StatusColecaoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITÓRIO: ColecaoRepository
 * DESCRIÇÃO: Operações de banco de dados para Coleções de Moda por marca e status.
 */
@Repository
public interface ColecaoRepository extends JpaRepository<Colecao, Long> {
    List<Colecao> findByMarcaId(Long marcaId);
    List<Colecao> findByMarcaIdAndStatus(Long marcaId, StatusColecaoEnum status);
}
