package com.modaflow.backend.controller;

import com.modaflow.backend.dto.request.CriarPecaRequestDTO;
import com.modaflow.backend.dto.request.AtualizarPecaRequestDTO;
import com.modaflow.backend.dto.response.PecaResponseDTO;
import com.modaflow.backend.enums.EtapaPecaEnum;
import com.modaflow.backend.service.PecaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CONTROLLER: PecaController
 * ENDPOINT BASE: /pecas
 * DESCRIÇÃO: Operações de Peças de vestuário (listagem com múltiplos filtros paginados, CRUD e movimentação de etapa).
 */
@RestController
@RequestMapping("/pecas")
@Tag(name = "Peças", description = "Endpoints de gerenciamento de peças de vestuário e ficha técnica")
public class PecaController {

    @Autowired
    private PecaService pecaService;

    @GetMapping
    @Operation(summary = "Listar peças paginadas com suporte a múltiplos filtros dinâmicos")
    public ResponseEntity<Page<PecaResponseDTO>> listarComFiltros(Pageable pageable) {
        return ResponseEntity.ok(pecaService.listarComFiltros(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar detalhes de uma peça por ID")
    public ResponseEntity<PecaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pecaService.buscarPorId(id));
    }

    @PostMapping
    @Operation(summary = "Cadastrar nova peça na coleção")
    public ResponseEntity<PecaResponseDTO> criar(@Valid @RequestBody CriarPecaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pecaService.criar(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar informações da peça")
    public ResponseEntity<PecaResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarPecaRequestDTO request) {
        return ResponseEntity.ok(pecaService.atualizar(id, request));
    }

    @PATCH("/{id}/etapa")
    @Operation(summary = "Mover peça para uma nova etapa do fluxo produtivo")
    public ResponseEntity<PecaResponseDTO> moverEtapa(@PathVariable Long id, @RequestParam EtapaPecaEnum novaEtapa) {
        return ResponseEntity.ok(pecaService.moverEtapa(id, novaEtapa));
    }
}
