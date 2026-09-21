package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.colecao.StatusColecaoEnum;
import com.modaflow.backend.common.dto.ErrorResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

/** Endpoints somente leitura da integração com o Linx. */
@RestController
@RequestMapping("/linx")
public class LinxIntegrationController {

    private static final int MAX_PAGE_SIZE = 200;

    private final Optional<LinxIntegrationService> linxIntegrationService;

    public LinxIntegrationController(Optional<LinxIntegrationService> linxIntegrationService) {
        this.linxIntegrationService = linxIntegrationService;
    }

    @GetMapping("/conexao")
    public ResponseEntity<LinxConnectionInfoDTO> getConnectionInfo() {
        return ResponseEntity.ok(service().getConnectionInfo());
    }

    @GetMapping("/produtos-validos")
    public ResponseEntity<LinxProdutosValidosPageResponseDTO> getProdutosValidos(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String griffe,
        @RequestParam(required = false) String colecao,
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) String material,
        @RequestParam(required = false) EtapaPecaEnum etapa,
        @RequestParam(required = false) LinxStatusPecaFiltro statusPeca,
        @RequestParam(required = false) StatusColecaoEnum statusColecao
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ResponseEntity.ok(
            service().getProdutosValidos(
                safePage,
                safeSize,
                search,
                griffe,
                colecao,
                tipo,
                material,
                etapa,
                statusPeca,
                statusColecao
            )
        );
    }

    @GetMapping("/colecoes-validas")
    public ResponseEntity<List<LinxColecaoValidaResponseDTO>> getColecoesValidas(
        @RequestParam(required = false) String griffe,
        @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service().getColecoesValidas(griffe, search));
    }

    @GetMapping("/opcoes")
    public ResponseEntity<LinxOpcoesResponseDTO> getOpcoesLinx() {
        return ResponseEntity.ok(service().getOpcoesLinx());
    }

    @GetMapping("/subgrupos")
    public ResponseEntity<List<String>> getSubgrupos() {
        return ResponseEntity.ok(service().getSubgrupos());
    }

    @GetMapping("/linhas")
    public ResponseEntity<List<String>> getLinhas() {
        return ResponseEntity.ok(service().getLinhas());
    }

    @GetMapping("/griffes")
    public ResponseEntity<List<String>> getGriffes() {
        return ResponseEntity.ok(service().getGriffes());
    }

    @GetMapping("/grades")
    public ResponseEntity<List<String>> getGrades() {
        return ResponseEntity.ok(service().getGrades());
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<String>> getCategorias() {
        return ResponseEntity.ok(service().getCategorias());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnavailable(ResponseStatusException error) {
        String message = error.getReason() == null ? "Integração Linx indisponível." : error.getReason();
        return ResponseEntity.status(error.getStatusCode())
            .body(new ErrorResponseDTO(error.getStatusCode().value(), message, LocalDateTime.now()));
    }

    private LinxIntegrationService service() {
        return linxIntegrationService.orElseThrow(() -> new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Integração Linx desativada. Configure MODAFLOW_LINX_ENABLED=true e as credenciais em .env.local."
        ));
    }
}
