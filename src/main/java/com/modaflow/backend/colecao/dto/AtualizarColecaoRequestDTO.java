package com.modaflow.backend.colecao.dto;

import com.modaflow.backend.colecao.StatusColecaoEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AtualizarColecaoRequestDTO {
    private String nome;
    private StatusColecaoEnum status;
    private LocalDate dataEntrega;
}
