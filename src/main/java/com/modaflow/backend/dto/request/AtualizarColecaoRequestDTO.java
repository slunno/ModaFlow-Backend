package com.modaflow.backend.dto.request;

import com.modaflow.backend.enums.StatusColecaoEnum;
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
