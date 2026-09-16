package com.modaflow.backend.peca;

import com.modaflow.backend.peca.dto.CriarPecaRequestDTO;
import com.modaflow.backend.peca.dto.AtualizarPecaRequestDTO;
import com.modaflow.backend.peca.dto.PecaResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;
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
