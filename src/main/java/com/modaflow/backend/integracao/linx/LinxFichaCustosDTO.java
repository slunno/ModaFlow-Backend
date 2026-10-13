package com.modaflow.backend.integracao.linx;

import java.util.List;
import java.util.Map;

/** Fontes de custos somente leitura. Diagnóstico não equivale ao total aprovado do portal. */
public record LinxFichaCustosDTO(
    LinxFichaVersaoDTO versao,
    String bancoOrigem,
    List<Map<String, Object>> tamanhos,
    List<Map<String, Object>> materiais,
    List<Map<String, Object>> fornecedores,
    List<Map<String, Object>> operacoes,
    List<Map<String, Object>> outrosCustos,
    List<Map<String, Object>> precosProduto,
    List<Map<String, Object>> diagnostico,
    List<String> avisos,
    String versaoIntegracao
) {
    public LinxFichaCustosDTO(LinxFichaVersaoDTO versao, String bancoOrigem, List<Map<String, Object>> tamanhos,
        List<Map<String, Object>> materiais, List<Map<String, Object>> fornecedores, List<Map<String, Object>> operacoes,
        List<Map<String, Object>> outrosCustos, List<Map<String, Object>> precosProduto,
        List<Map<String, Object>> diagnostico, List<String> avisos) {
        this(versao, bancoOrigem, tamanhos, materiais, fornecedores, operacoes, outrosCustos, precosProduto, diagnostico, avisos, "custos-v1");
    }
}
