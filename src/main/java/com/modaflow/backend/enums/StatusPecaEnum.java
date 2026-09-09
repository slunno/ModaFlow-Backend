package com.modaflow.backend.enums;

/**
 * ============================================================================
 * ENUM: StatusPecaEnum
 * DESCRIÇÃO: Status geral de aprovação e vida de uma peça de vestuário.
 * USADO EM: Peca entity, filtros da PecasTab e relatórios.
 * ============================================================================
 */
public enum StatusPecaEnum {
    /** Em fase de criação e ajustes */
    EM_DESENVOLVIMENTO,
    /** Aprovada para produção industrial */
    APROVADO,
    /** Cancelada / Reprovada pelo estilo */
    CANCELADO,
    /** Em processo de costura e lote industrial */
    EM_PRODUCAO
}
