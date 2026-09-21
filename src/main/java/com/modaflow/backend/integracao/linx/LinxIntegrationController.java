package com.modaflow.backend.integracao.linx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints somente leitura da integração com o Linx. */
@RestController
@RequestMapping("/linx")
@ConditionalOnProperty(prefix = "modaflow.linx", name = "enabled", havingValue = "true")
public class LinxIntegrationController {

    private static final int MAX_PAGE_SIZE = 200;

    private final LinxIntegrationService linxIntegrationService;

    public LinxIntegrationController(LinxIntegrationService linxIntegrationService) {
        this.linxIntegrationService = linxIntegrationService;
    }

    @GetMapping("/conexao")
    public ResponseEntity<LinxConnectionInfoDTO> getConnectionInfo() {
        return ResponseEntity.ok(linxIntegrationService.getConnectionInfo());
    }

    @GetMapping("/produtos-validos")
    public ResponseEntity<LinxProdutosValidosPageResponseDTO> getProdutosValidos(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String griffe,
        @RequestParam(required = false) String colecao,
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) String material
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ResponseEntity.ok(
            linxIntegrationService.getProdutosValidos(
                safePage,
                safeSize,
                search,
                griffe,
                colecao,
                tipo,
                material
            )
        );
    }

    @GetMapping("/colecoes-validas")
    public ResponseEntity<List<LinxColecaoValidaResponseDTO>> getColecoesValidas(
        @RequestParam(required = false) String griffe,
        @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(linxIntegrationService.getColecoesValidas(griffe, search));
    }

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
