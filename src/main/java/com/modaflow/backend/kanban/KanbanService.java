package com.modaflow.backend.kanban;

import com.modaflow.backend.peca.dto.PecaKanbanResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;

import java.util.List;

/**
 * SERVIÇO: KanbanService
 * DESCRIÇÃO: Operações para o Quadro Kanban de 23 etapas de produção (carregar colunas, mover peças).
 */
public interface KanbanService {
    List<PecaKanbanResponseDTO> obterPecasDoKanban();
    void moverPecaNoKanban(Long pecaId, EtapaPecaEnum novaEtapa);
}
