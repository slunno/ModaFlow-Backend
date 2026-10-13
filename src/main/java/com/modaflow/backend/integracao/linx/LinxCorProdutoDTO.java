package com.modaflow.backend.integracao.linx;

/** Vínculo existente do produto; mantém separadas as duas identidades de cor. */
public record LinxCorProdutoDTO(String produto, String corProduto, String descricao,
    String corBasica, String descricaoBasica, String colecaoCor, boolean inativa) {}
