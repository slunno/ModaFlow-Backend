package com.modaflow.backend.enums;

/**
 * ============================================================================
 * ENUM: StatusColecaoEnum
 * DESCRIÇÃO: Estados possíveis de uma coleção de moda.
 * USADO EM: Colecao entity, ColecoesTab filtro de coleções na Home/Relatórios.
 * ============================================================================
 */
public enum StatusColecaoEnum {
    /** Coleção com peças em desenvolvimento ativo */
    EM_ANDAMENTO,
    /** Coleção concluída e pronta para produção/lançamento */
    CONCLUIDO,
    /** Coleção encerrada e arquivada */
    ARQUIVADO
}
