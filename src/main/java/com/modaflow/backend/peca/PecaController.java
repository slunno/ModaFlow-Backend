package com.modaflow.backend.peca;

import com.modaflow.backend.peca.dto.CriarPecaRequestDTO;
import com.modaflow.backend.peca.dto.AtualizarPecaRequestDTO;
import com.modaflow.backend.peca.dto.PecaResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.modaflow.backend.peca.PecaService;
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
 */
@RestController
@RequestMapping("/pecas")
@Tag(name = "Peças", description = "Endpoints de gerenciamento de peças de vestuário")
public class PecaController {

    @Autowired
    private PecaService pecaService;

    @GetMapping
    @Operation(summary = "Listar peças paginadas com filtros dinâmicos")
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

    @PatchMapping("/{id}/etapa")
    @Operation(summary = "Mover peça para uma nova etapa do fluxo produtivo")
    public ResponseEntity<PecaResponseDTO> moverEtapa(@PathVariable Long id, @RequestParam EtapaPecaEnum novaEtapa) {
        return ResponseEntity.ok(pecaService.moverEtapa(id, novaEtapa));
    }

    @PostMapping("/{id}/entrega")
    @Operation(summary = "Entregar peça após conferir a ficha enviada; não integra no Linx")
    public ResponseEntity<PecaResponseDTO> entregarFicha(@PathVariable Long id,
            @RequestParam EtapaPecaEnum novaEtapa,
            @Valid @RequestBody com.modaflow.backend.ficha.FichaConferenciaRequest ficha) {
        return ResponseEntity.ok(pecaService.entregarFicha(id, novaEtapa, ficha));
    }
}
