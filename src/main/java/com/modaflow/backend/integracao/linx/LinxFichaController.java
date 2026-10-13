package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.dto.ErrorResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/linx")
public class LinxFichaController {
    private final Optional<LinxFichaService> fichas;
    private final Optional<LinxFichaCustosService> custos;
    public LinxFichaController(Optional<LinxFichaService> fichas, Optional<LinxFichaCustosService> custos) {
        this.fichas = fichas;
        this.custos = custos;
    }

    @GetMapping("/produtos/{produto}/fichas/{versao}/custos")
    public LinxFichaCustosDTO getCustos(@PathVariable String produto, @PathVariable String versao,
                                      @RequestParam String griffe, @RequestParam String colecao,
                                      @RequestParam String descColecao, @RequestParam(defaultValue = "false") boolean legado) {
        return custos.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Integração Linx desativada.")).getCustos(produto, versao, griffe, colecao, descColecao, legado);
    }

    @GetMapping("/produtos/{produto}/fichas/{versao}/custos/v2")
    public LinxFichaCustosDTO getCustosCompletos(@PathVariable String produto, @PathVariable String versao,
        @RequestParam String griffe, @RequestParam String colecao, @RequestParam String descColecao,
        @RequestParam(defaultValue = "false") boolean legado) {
        return custos.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Integração Linx desativada.")).getCustosCompletos(produto, versao, griffe, colecao, descColecao, legado);
    }

    @GetMapping("/produtos/{produto}/fichas")
    public List<LinxFichaVersaoDTO> getVersoes(@PathVariable String produto, @RequestParam String griffe,
                                             @RequestParam String colecao, @RequestParam String descColecao) {
        return service().getVersoes(produto, griffe, colecao, descColecao);
    }
    @GetMapping("/produtos/{produto}/fichas/{versao}")
    public LinxFichaDetalheDTO getDetalhe(@PathVariable String produto, @PathVariable String versao, @RequestParam String griffe,
                                        @RequestParam String colecao, @RequestParam String descColecao) {
        return service().getDetalhe(produto, versao, griffe, colecao, descColecao);
    }
    @GetMapping("/colecoes/{colecao}/produtos/{produto}/ficha-atual")
    public LinxFichaDetalheDTO getAtual(@PathVariable String colecao, @PathVariable String produto,
                                      @RequestParam String griffe, @RequestParam String descColecao) {
        return service().getAtual(produto, griffe, colecao, descColecao);
    }
    @GetMapping("/colecoes/{colecao}/produtos/{produto}/cores")
    public List<LinxCorProdutoDTO> getCoresProduto(@PathVariable("colecao") String colecao,
        @PathVariable("produto") String produto, @RequestParam("griffe") String griffe,
        @RequestParam("descColecao") String descColecao) {
        return service().getCoresProduto(produto, griffe, colecao, descColecao);
    }
    @GetMapping("/colecoes/{colecao}/fichas-resumo")
    public List<LinxFichaResumoDTO> getResumos(@PathVariable String colecao, @RequestParam String griffe,
                                             @RequestParam String descColecao, @RequestParam(defaultValue = "false") boolean legado) {
        return service().getResumosDaColecao(colecao, griffe, descColecao, legado);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnavailable(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(new ErrorResponseDTO(
            error.getStatusCode().value(), error.getReason(), LocalDateTime.now()));
    }
    private LinxFichaService service() {
        return fichas.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integração Linx desativada."));
    }
}
