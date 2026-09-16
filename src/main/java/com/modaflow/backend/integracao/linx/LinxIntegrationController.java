package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.integracao.linx.LinxOpcoesResponseDTO;
import com.modaflow.backend.integracao.linx.LinxIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================================
 * CONTROLLER: LinxIntegrationController
 * ENDPOINTS: /linx/*
 * DESCRIÇÃO: Expõe APIs para recuperar opções nativas do Linx no SQL Server
 *            (Sub-grupos, Linhas, Griffes, Grades, Categorias).
 * ============================================================================
 */
@RestController
@RequestMapping("/linx")
public class LinxIntegrationController {

    @Autowired
    private LinxIntegrationService linxIntegrationService;

    @GetMapping("/opcoes")
    public ResponseEntity<LinxOpcoesResponseDTO> getOpcoesLinx() {
        return ResponseEntity.ok(linxIntegrationService.getOpcoesLinx());
    }

    @GetMapping("/subgrupos")
    public ResponseEntity<List<String>> getSubgrupos() {
        return ResponseEntity.ok(linxIntegrationService.getSubgrupos());
    }

    @GetMapping("/linhas")
    public ResponseEntity<List<String>> getLinhas() {
        return ResponseEntity.ok(linxIntegrationService.getLinhas());
    }

    @GetMapping("/griffes")
    public ResponseEntity<List<String>> getGriffes() {
        return ResponseEntity.ok(linxIntegrationService.getGriffes());
    }

    @GetMapping("/grades")
    public ResponseEntity<List<String>> getGrades() {
        return ResponseEntity.ok(linxIntegrationService.getGrades());
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<String>> getCategorias() {
        return ResponseEntity.ok(linxIntegrationService.getCategorias());
    }
}
