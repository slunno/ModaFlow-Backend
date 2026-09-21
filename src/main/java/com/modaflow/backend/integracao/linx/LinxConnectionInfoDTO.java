package com.modaflow.backend.integracao.linx;

import java.util.List;

/**
 * Identifica a conexão usada pelas consultas de integração com o Linx.
 */
public record LinxConnectionInfoDTO(
    String servidor,
    String banco,
    String bancoEsperado,
    boolean bancoEsperadoConfirmado,
    boolean credencialSomenteLeitura,
    boolean integracaoSomenteLeitura,
    List<String> permissoesEscritaDetectadas
) {}
