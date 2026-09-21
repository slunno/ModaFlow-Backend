package com.modaflow.backend.integracao.linx;

import java.time.LocalDateTime;

/**
 * Coleção do Linx que possui ao menos um produto válido nas griffes atendidas.
 */
public record LinxColecaoValidaResponseDTO(
    String codigo,
    String nome,
    String griffeProduto,
    String griffeColecao,
    LocalDateTime dataInicio,
    LocalDateTime dataFinal,
    String temporada,
    long quantidadeProdutos
) {}
