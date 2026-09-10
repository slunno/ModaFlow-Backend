package com.modaflow.backend.dto.request;

import com.modaflow.backend.enums.TipoInsumoEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CriarInsumoRequestDTO {
    @NotBlank(message = "O nome é obrigatório")
    private String nome;
    @NotBlank(message = "O código é obrigatório")
    private String codigo;
    @NotNull(message = "O tipo é obrigatório (TECIDO ou AVIAMENTO)")
    private TipoInsumoEnum tipo;
    private Boolean temErp = false;
    private String imagemUrl;
    private String fornecedores;
    private BigDecimal custo;
    private String unidade;
}
