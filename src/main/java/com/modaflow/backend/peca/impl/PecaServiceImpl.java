package com.modaflow.backend.peca.impl;

import com.modaflow.backend.peca.dto.CriarPecaRequestDTO;
import com.modaflow.backend.peca.dto.AtualizarPecaRequestDTO;
import com.modaflow.backend.peca.dto.PecaResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.modaflow.backend.peca.PecaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * SERVIÇO (IMPL): PecaServiceImpl
 * DESCRIÇÃO: Lógica de peças de roupa, cálculo de atraso em dias e avanço de etapa no PLM.
 */
@Service
public class PecaServiceImpl implements PecaService {

    @Override
    public Page<PecaResponseDTO> listarComFiltros(Pageable pageable) {
        return Page.empty();
    }

    @Override
    public PecaResponseDTO buscarPorId(Long id) {
        return null;
    }

    @Override
    public PecaResponseDTO criar(CriarPecaRequestDTO request) {
        return null;
    }

    @Override
    public PecaResponseDTO atualizar(Long id, AtualizarPecaRequestDTO request) {
        return null;
    }

    @Override
    public PecaResponseDTO moverEtapa(Long id, EtapaPecaEnum novaEtapa) {
        return null;
    }
}
