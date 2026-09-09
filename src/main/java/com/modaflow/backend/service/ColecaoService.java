package com.modaflow.backend.service;

import com.modaflow.backend.dto.request.CriarColecaoRequestDTO;
import com.modaflow.backend.dto.request.AtualizarColecaoRequestDTO;
import com.modaflow.backend.dto.response.ColecaoDetalheResponseDTO;
import com.modaflow.backend.dto.response.ColecaoResponseDTO;
import com.modaflow.backend.enums.StatusColecaoEnum;

import java.util.List;

/**
 * SERVIÇO: ColecaoService
 * DESCRIÇÃO: Gestão de Coleções de Moda por marca.
 */
public interface ColecaoService {
    List<ColecaoResponseDTO> listarPorStatus(StatusColecaoEnum status);
    ColecaoDetalheResponseDTO buscarDetalhesComPecas(Long id);
    ColecaoResponseDTO criar(CriarColecaoRequestDTO request);
    ColecaoResponseDTO atualizar(Long id, AtualizarColecaoRequestDTO request);
}
