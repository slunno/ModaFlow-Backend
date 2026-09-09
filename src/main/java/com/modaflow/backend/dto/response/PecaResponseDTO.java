package com.modaflow.backend.dto.response;

import com.modaflow.backend.enums.EtapaPecaEnum;
import com.modaflow.backend.enums.StatusPecaEnum;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO: PecaResponseDTO
 * DESCRIÇÃO: Representação completa de uma peça de vestuário e seu progresso no PLM.
 */
@Getter
@Setter
public class PecaResponseDTO {
    private Long id;
    private String codigo;
    private String nome;
    private String tipo;
    private StatusPecaEnum status;
    private EtapaPecaEnum etapaAtual;
    private String tema;
    private String estilista;
    private String modelista;
    private String imagemCroquiUrl;
    private BigDecimal custo;
    private BigDecimal preco;
    private LocalDate previsaoEntrega;
    private Integer diasAtraso;
    private Long colecaoId;
    private String colecaoNome;
}
