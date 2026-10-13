package com.modaflow.backend.integracao.linx;

import java.util.List;

public record LinxFichaDetalheDTO(LinxFichaVersaoDTO versao, List<LinxFichaMaterialDTO> materiais) {}
