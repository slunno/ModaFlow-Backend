package com.modaflow.backend.service;

import com.modaflow.backend.dto.response.PecaKanbanResponseDTO;
import com.modaflow.backend.enums.EtapaPecaEnum;

import java.util.List;

/**
 * SERVIÇO: KanbanService
 * DESCRIÇÃO: Operações para o Quadro Kanban de 23 etapas de produção (carregar colunas, mover peças).
 */
public interface KanbanService {
    List<PecaKanbanResponseDTO> obterPecasDoKanban();
    void moverPecaNoKanban(Long pecaId, EtapaPecaEnum novaEtapa);
}
