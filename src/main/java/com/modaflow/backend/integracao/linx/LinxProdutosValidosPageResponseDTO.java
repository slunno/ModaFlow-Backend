package com.modaflow.backend.integracao.linx;

import java.util.List;

/**
 * Página estável para a listagem read-only de produtos válidos no Linx.
 */
public record LinxProdutosValidosPageResponseDTO(
    List<LinxProdutoValidoResponseDTO> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    String sourceDatabase,
    boolean readOnly
) {}
