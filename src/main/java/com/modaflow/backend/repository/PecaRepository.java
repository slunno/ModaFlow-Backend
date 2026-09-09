package com.modaflow.backend.repository;

import com.modaflow.backend.entity.Peca;
import com.modaflow.backend.enums.EtapaPecaEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITÓRIO: PecaRepository
 * DESCRIÇÃO: Operações de banco de dados para Peças de vestuário, com suporte a JpaSpecificationExecutor
 *            para filtros dinâmicos (marca, etapa, tipo, status, coleção, tecido, busca livre).
 */
@Repository
public interface PecaRepository extends JpaRepository<Peca, Long>, JpaSpecificationExecutor<Peca> {
    List<Peca> findByMarcaIdAndEtapaAtual(Long marcaId, EtapaPecaEnum etapa);
    List<Peca> findByColecaoId(Long colecaoId);
    Page<Peca> findByMarcaId(Long marcaId, Pageable pageable);
}
