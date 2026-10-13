package com.modaflow.backend.integracao.linx;

import java.time.LocalDateTime;

public record LinxFichaVersaoDTO(
    String produto, String versao, String descricao, LocalDateTime dataCriacao,
    String responsavel, String status, String colecaoCodigo, String colecaoNome
) {}
