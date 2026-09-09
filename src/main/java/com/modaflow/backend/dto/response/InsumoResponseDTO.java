package com.modaflow.backend.dto.response;

import com.modaflow.backend.enums.TipoInsumoEnum;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO: InsumoResponseDTO
 * DESCRIÇÃO: Representação de Tecidos e Aviamentos.
 */
@Getter
@Setter
public class InsumoResponseDTO {
    private Long id;
    private String nome;
    private String codigo;
    private TipoInsumoEnum tipo;
    private Boolean temErp;
    private String imagemUrl;
    private String fornecedores;
    private BigDecimal custo;
    private String unidade;
}
