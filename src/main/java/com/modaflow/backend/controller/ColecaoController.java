package com.modaflow.backend.controller;

import com.modaflow.backend.dto.request.CriarColecaoRequestDTO;
import com.modaflow.backend.dto.request.AtualizarColecaoRequestDTO;
import com.modaflow.backend.dto.response.ColecaoDetalheResponseDTO;
import com.modaflow.backend.dto.response.ColecaoResponseDTO;
import com.modaflow.backend.enums.StatusColecaoEnum;
import com.modaflow.backend.service.ColecaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CONTROLLER: ColecaoController
 * ENDPOINT BASE: /colecoes
 * DESCRIÇÃO: Operações de listagem, detalhamento e criação de coleções de moda da marca ativa.
 */
@RestController
@RequestMapping("/colecoes")
@Tag(name = "Coleções", description = "Endpoints de gerenciamento de coleções de moda")
public class ColecaoController {

    @Autowired
    private ColecaoService colecaoService;

    @GetMapping
    @Operation(summary = "Listar coleções filtradas por status (EM_ANDAMENTO, CONCLUIDO, ARQUIVADO)")
    public ResponseEntity<List<ColecaoResponseDTO>> listarPorStatus(@RequestParam(required = false) StatusColecaoEnum status) {
        return ResponseEntity.ok(colecaoService.listarPorStatus(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar detalhe completo de uma coleção com lista de peças vinculadas")
    public ResponseEntity<ColecaoDetalheResponseDTO> buscarDetalhesComPecas(@PathVariable Long id) {
        return ResponseEntity.ok(colecaoService.buscarDetalhesComPecas(id));
    }

    @PostMapping
    @Operation(summary = "Criar nova coleção")
    public ResponseEntity<ColecaoResponseDTO> criar(@Valid @RequestBody CriarColecaoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(colecaoService.criar(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar coleção existente")
    public ResponseEntity<ColecaoResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarColecaoRequestDTO request) {
        return ResponseEntity.ok(colecaoService.atualizar(id, request));
    }
}
