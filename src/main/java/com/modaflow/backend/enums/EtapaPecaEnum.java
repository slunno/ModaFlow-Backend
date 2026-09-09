package com.modaflow.backend.enums;

/**
 * ============================================================================
 * ENUM: EtapaPecaEnum
 * DESCRIÇÃO: As 23 etapas de desenvolvimento de produto no ModaFlow PLM.
 * USADO EM: Peca entity, Kanban Board drag-and-drop, Dashboard por etapa.
 * ============================================================================
 */
public enum EtapaPecaEnum {
    BRIEFING,
    DESENHO_TECNICO,
    APROVACAO_ESTILO,
    CAD_MODELAGEM,
    PILOTAGEM_CORTE,
    PILOTAGEM_COSTURA,
    PROVA_ROUPA_1,
    AJUSTE_MODELAGEM,
    PROVA_ROUPA_2,
    APROVACAO_PILOTO,
    FICHA_TECNICA_FINAL,
    PRECO_CUSTO,
    COMPRA_INSUMOS,
    RECEBIMENTO_TECIDO,
    CORTE_PRODUCAO,
    BORDADO_ESTAMPA,
    COSTURA_PRODUCAO,
    PASSADORIA,
    REVISAO_QUALIDADE,
    EMBALAGEM,
    LOGISTICA,
    EXPEDICAO_LOJAS,
    DISPONIVEL_VENDA
}
