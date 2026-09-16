package com.modaflow.backend.colecao.dto;

import com.modaflow.backend.colecao.StatusColecaoEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CriarColecaoRequestDTO {
    @NotBlank(message = "O nome da coleção é obrigatório")
    private String nome;
    @NotNull(message = "O status é obrigatório")
    private StatusColecaoEnum status;
    private LocalDate dataEntrega;
}
