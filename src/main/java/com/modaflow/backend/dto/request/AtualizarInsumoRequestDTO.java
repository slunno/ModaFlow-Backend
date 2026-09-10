package com.modaflow.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AtualizarInsumoRequestDTO {
    private String nome;
    private String codigo;
    private Boolean temErp;
    private String imagemUrl;
    private String fornecedores;
    private BigDecimal custo;
    private String unidade;
}
