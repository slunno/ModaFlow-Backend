package com.modaflow.backend.integracao.linx;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Dados de leitura do cadastro de um material Linx, sem sua composição manual do portal. */
public record LinxMaterialCadastroDTO(
    String codigo,
    String nome,
    String grupo,
    String unidadeEstoque,
    String unidadeFichaTecnica,
    String largura,
    String comprimento,
    String altura,
    BigDecimal rendimento,
    List<Map<String, Object>> cores,
    BigDecimal custoAVista,
    BigDecimal custoReposicao,
    List<Map<String, Object>> fornecedores
) { }
