package com.modaflow.backend.service.impl;

import com.modaflow.backend.dto.request.CriarColecaoRequestDTO;
import com.modaflow.backend.dto.request.AtualizarColecaoRequestDTO;
import com.modaflow.backend.dto.response.ColecaoDetalheResponseDTO;
import com.modaflow.backend.dto.response.ColecaoResponseDTO;
import com.modaflow.backend.enums.StatusColecaoEnum;
import com.modaflow.backend.service.ColecaoService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SERVIÇO (IMPL): ColecaoServiceImpl
 * DESCRIÇÃO: Lógica de negócio para coleções de moda, cálculo de progresso (%) e peças concluídas.
 */
@Service
public class ColecaoServiceImpl implements ColecaoService {

    @Override
    public List<ColecaoResponseDTO> listarPorStatus(StatusColecaoEnum status) {
        return List.of();
    }

    @Override
    public ColecaoDetalheResponseDTO buscarDetalhesComPecas(Long id) {
        return null;
    }

    @Override
    public ColecaoResponseDTO criar(CriarColecaoRequestDTO request) {
        return null;
    }

    @Override
    public ColecaoResponseDTO atualizar(Long id, AtualizarColecaoRequestDTO request) {
        return null;
    }
}
