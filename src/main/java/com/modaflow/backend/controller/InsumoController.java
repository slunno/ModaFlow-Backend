package com.modaflow.backend.controller;

import com.modaflow.backend.dto.request.CriarInsumoRequestDTO;
import com.modaflow.backend.dto.request.AtualizarInsumoRequestDTO;
import com.modaflow.backend.dto.response.InsumoResponseDTO;
import com.modaflow.backend.enums.TipoInsumoEnum;
import com.modaflow.backend.service.InsumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * CONTROLLER: InsumoController
 * ENDPOINT BASE: /insumos
 * DESCRIÇÃO: Operações de Tecidos e Aviamentos (listagem por tipo TECIDO/AVIAMENTO, CRUD e importação CSV/Excel).
 */
@RestController
@RequestMapping("/insumos")
@Tag(name = "Insumos (Tecidos & Aviamentos)", description = "Gestão de insumos e matérias-primas por marca")
public class InsumoController {

    @Autowired
    private InsumoService insumoService;

    @GetMapping
    @Operation(summary = "Listar insumos paginados filtrados por tipo (TECIDO ou AVIAMENTO)")
    public ResponseEntity<Page<InsumoResponseDTO>> listarPorTipo(@RequestParam TipoInsumoEnum tipo, Pageable pageable) {
        return ResponseEntity.ok(insumoService.listarPorTipo(tipo, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar insumo por ID")
    public ResponseEntity<InsumoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(insumoService.buscarPorId(id));
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo insumo (Tecido ou Aviamento)")
    public ResponseEntity<InsumoResponseDTO> criar(@Valid @RequestBody CriarInsumoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(insumoService.criar(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar insumo existente")
    public ResponseEntity<InsumoResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarInsumoRequestDTO request) {
        return ResponseEntity.ok(insumoService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir insumo por ID")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        insumoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/importar")
    @Operation(summary = "Importar insumos em lote via arquivo CSV/Excel")
    public ResponseEntity<Integer> importarLote(@RequestParam("file") MultipartFile file, @RequestParam TipoInsumoEnum tipo) {
        int importados = insumoService.importarLote(file, tipo);
        return ResponseEntity.ok(importados);
    }
}
