package com.modaflow.backend.colecao.dto;

import com.modaflow.backend.colecao.StatusColecaoEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO: ColecaoResponseDTO
 * DESCRIÇÃO: Representação resumida de uma coleção de moda com campos derivados progressoPercent e pecasConcluidas.
 */
@Getter
@Setter
public class ColecaoResponseDTO {
    private Long id;
    private String nome;
    private StatusColecaoEnum status;
    private Integer progressoPercent;
    private Integer pecasConcluidas;
    private Integer pecasTotal;
    private LocalDate concluidoEmDate;
    private LocalDate dataEntrega;
    private Integer diasAtraso;
}
