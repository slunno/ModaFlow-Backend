package com.modaflow.backend.dto.response;

import com.modaflow.backend.enums.StatusColecaoEnum;
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
