package com.modaflow.backend.integracao.linx;

import java.util.List;

/** Materiais da ficha selecionada; no legado pode ser a ficha atual sem U_COLECAO da peça desta coleção. */
public record LinxFichaResumoDTO(String produto, String versao, List<String> tecidos, List<String> aviamentos) {}
