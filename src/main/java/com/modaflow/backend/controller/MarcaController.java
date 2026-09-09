package com.modaflow.backend.controller;

import com.modaflow.backend.dto.request.CriarMarcaRequestDTO;
import com.modaflow.backend.dto.request.AtualizarMarcaRequestDTO;
import com.modaflow.backend.dto.response.MarcaResponseDTO;
import com.modaflow.backend.service.MarcaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CONTROLLER: MarcaController
 * ENDPOINT BASE: /marcas
 * DESCRIÇÃO: Operações de consulta, criação e atualização de marcas (tenants).
 */
@RestController
@RequestMapping("/marcas")
@Tag(name = "Marcas", description = "Endpoints de gerenciamento de marcas da AKR BRANDS")
public class MarcaController {

    @Autowired
    private MarcaService marcaService;

    @GetMapping
    @Operation(summary = "Listar todas as marcas da empresa")
    public ResponseEntity<List<MarcaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(marcaService.listarTodas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar detalhes de uma marca por ID")
    public ResponseEntity<MarcaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(marcaService.buscarPorId(id));
    }

    @PostMapping
    @Operation(summary = "Criar nova marca no sistema")
    public ResponseEntity<MarcaResponseDTO> criar(@Valid @RequestBody CriarMarcaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(marcaService.criar(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar informações de uma marca existente")
    public ResponseEntity<MarcaResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarMarcaRequestDTO request) {
        return ResponseEntity.ok(marcaService.atualizar(id, request));
    }
}
