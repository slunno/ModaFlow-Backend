package com.modaflow.backend.service;

import com.modaflow.backend.dto.request.CriarPecaRequestDTO;
import com.modaflow.backend.dto.request.AtualizarPecaRequestDTO;
import com.modaflow.backend.dto.response.PecaResponseDTO;
import com.modaflow.backend.enums.EtapaPecaEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * SERVIÇO: PecaService
 * DESCRIÇÃO: Operações com Peças de vestuário (listagem paginada com filtros dinâmicos, CRUD, mover etapa).
 */
public interface PecaService {
    Page<PecaResponseDTO> listarComFiltros(Pageable pageable);
    PecaResponseDTO buscarPorId(Long id);
    PecaResponseDTO criar(CriarPecaRequestDTO request);
    PecaResponseDTO atualizar(Long id, AtualizarPecaRequestDTO request);
    PecaResponseDTO moverEtapa(Long id, EtapaPecaEnum novaEtapa);
}
