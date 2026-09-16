package com.modaflow.backend.kanban.impl;

import com.modaflow.backend.peca.dto.PecaKanbanResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.modaflow.backend.kanban.KanbanService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SERVIÇO (IMPL): KanbanServiceImpl
 * DESCRIÇÃO: Lógica para agrupamento de peças por etapa e atualização de colunas do Kanban.
 */
@Service
public class KanbanServiceImpl implements KanbanService {

    @Override
    public List<PecaKanbanResponseDTO> obterPecasDoKanban() {
        return List.of();
    }

    @Override
    public void moverPecaNoKanban(Long pecaId, EtapaPecaEnum novaEtapa) {
    }
}
