package com.modaflow.backend.colecao.impl;

import com.modaflow.backend.colecao.dto.CriarColecaoRequestDTO;
import com.modaflow.backend.colecao.dto.AtualizarColecaoRequestDTO;
import com.modaflow.backend.colecao.dto.ColecaoDetalheResponseDTO;
import com.modaflow.backend.colecao.dto.ColecaoResponseDTO;
import com.modaflow.backend.colecao.StatusColecaoEnum;
import com.modaflow.backend.colecao.ColecaoService;
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
