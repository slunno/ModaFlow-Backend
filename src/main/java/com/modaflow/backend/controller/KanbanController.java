package com.modaflow.backend.controller;

import com.modaflow.backend.dto.response.PecaKanbanResponseDTO;
import com.modaflow.backend.enums.EtapaPecaEnum;
import com.modaflow.backend.service.KanbanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CONTROLLER: KanbanController
 * ENDPOINT BASE: /kanban
 */
@RestController
@RequestMapping("/kanban")
@Tag(name = "Kanban", description = "Endpoints para quadro Kanban de 23 etapas com drag-and-drop")
public class KanbanController {

    @Autowired
    private KanbanService kanbanService;

    @GetMapping
    @Operation(summary = "Obter todas as peças ativas agrupadas para o quadro Kanban")
    public ResponseEntity<List<PecaKanbanResponseDTO>> obterPecasDoKanban() {
        return ResponseEntity.ok(kanbanService.obterPecasDoKanban());
    }

    @PatchMapping("/pecas/{id}/etapa")
    @Operation(summary = "Mover peça no Kanban para uma nova coluna/etapa")
    public ResponseEntity<Void> moverPecaNoKanban(@PathVariable Long id, @RequestParam EtapaPecaEnum novaEtapa) {
        kanbanService.moverPecaNoKanban(id, novaEtapa);
        return ResponseEntity.noContent().build();
    }
}
