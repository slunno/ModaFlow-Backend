package com.modaflow.backend.dto.request;

import com.modaflow.backend.enums.StatusPecaEnum;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class AtualizarPecaRequestDTO {
    private String nome;
    private String tipo;
    private StatusPecaEnum status;
    private String tema;
    private String estilista;
    private String modelista;
    private String imagemCroquiUrl;
    private BigDecimal custo;
    private BigDecimal preco;
    private LocalDate previsaoEntrega;
}
