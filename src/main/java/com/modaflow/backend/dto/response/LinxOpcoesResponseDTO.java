package com.modaflow.backend.dto.response;

import java.util.List;

/**
 * DTO com a lista de opções de integração com o Linx trazidas do SQL Server.
 */
public record LinxOpcoesResponseDTO(
    List<String> subgrupos,
    List<String> linhas,
    List<String> griffes,
    List<String> grades,
    List<String> categorias
) {}
