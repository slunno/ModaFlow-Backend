package com.modaflow.backend.colecao.dto;

import com.modaflow.backend.peca.dto.PecaResponseDTO;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO: ColecaoDetalheResponseDTO
 * DESCRIÇÃO: Detalhe completo de uma coleção contendo a lista de suas peças.
 */
@Getter
@Setter
public class ColecaoDetalheResponseDTO extends ColecaoResponseDTO {
    private List<PecaResponseDTO> pecas;
}
