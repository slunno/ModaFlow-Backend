package com.modaflow.backend.dto.response;

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
