package com.modaflow.backend.integracao.linx;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Produto oficial do Linx que possui uma coleção válida e pode alimentar o
 * novo PLM. O código original é preservado junto da versão normalizada usada
 * para cruzamento com o CM.
 */
public record LinxProdutoValidoResponseDTO(
    Long linxId,
    String codigo,
    String codigoNormalizado,
    String descricao,
    String griffe,
    String colecaoCodigo,
    String colecaoNome,
    String griffeColecao,
    LocalDateTime dataInicioColecao,
    LocalDateTime dataFinalColecao,
    String temporada,
    String grupo,
    String subgrupo,
    String tipo,
    String periodoPcp,
    String grade,
    String linha,
    String cartela,
    String materialPrincipal,
    String descricaoMaterialPrincipal,
    String modelagem,
    String estilista,
    String modelista,
    String statusProduto,
    String tipoStatusProduto,
    LocalDateTime dataCadastramento,
    LocalDateTime dataInicioDesenvolvimento,
    BigDecimal custoReposicao,
    BigDecimal precoReposicao
) {}
