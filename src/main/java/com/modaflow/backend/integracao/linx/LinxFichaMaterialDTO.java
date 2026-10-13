package com.modaflow.backend.integracao.linx;

import java.util.List;
import java.util.Map;

/** Preserva os nomes e valores da fonte, sem supor equivalências ou calcular custos. */
public record LinxFichaMaterialDTO(
    String codigo, String nome, String grupo, String item,
    Map<String, Object> dados, List<Map<String, Object>> cores
) {}
