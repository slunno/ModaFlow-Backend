package com.modaflow.backend.dto.response;

import com.modaflow.backend.enums.EtapaPecaEnum;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO: PecaKanbanResponseDTO
 * DESCRIÇÃO: Representação leve de peça para exibição em cartões do quadro Kanban.
 */
@Getter
@Setter
public class PecaKanbanResponseDTO {
    private Long id;
    private String codigo;
    private String nome;
    private String imagemCroquiUrl;
    private EtapaPecaEnum etapaAtual;
    private String estilista;
    private Integer diasAtraso;
}
