package com.modaflow.backend.colecao;

import com.modaflow.backend.colecao.Colecao;
import com.modaflow.backend.colecao.StatusColecaoEnum;
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
