package com.modaflow.backend.colecao;

import com.modaflow.backend.colecao.dto.CriarColecaoRequestDTO;
import com.modaflow.backend.colecao.dto.AtualizarColecaoRequestDTO;
import com.modaflow.backend.colecao.dto.ColecaoDetalheResponseDTO;
import com.modaflow.backend.colecao.dto.ColecaoResponseDTO;
import com.modaflow.backend.colecao.StatusColecaoEnum;

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
