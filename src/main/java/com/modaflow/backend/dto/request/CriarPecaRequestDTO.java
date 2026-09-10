package com.modaflow.backend.dto.request;

import com.modaflow.backend.enums.EtapaPecaEnum;
import com.modaflow.backend.enums.StatusPecaEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CriarPecaRequestDTO {
    @NotBlank(message = "O código é obrigatório")
    private String codigo;
    @NotBlank(message = "O nome é obrigatório")
    private String nome;
    private String tipo;
    @NotNull(message = "O status é obrigatório")
    private StatusPecaEnum status;
    @NotNull(message = "A etapa atual é obrigatória")
    private EtapaPecaEnum etapaAtual;
    private String tema;
    private String estilista;
    private String modelista;
    private String imagemCroquiUrl;
    private BigDecimal custo;
    private BigDecimal preco;
    private LocalDate previsaoEntrega;
    @NotNull(message = "O ID da coleção é obrigatório")
    private Long colecaoId;
}
