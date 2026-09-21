package com.modaflow.backend.peca;

/**
 * ============================================================================
 * ENUM: EtapaPecaEnum
 * DESCRIÇÃO: As 23 etapas de desenvolvimento de produto no ModaFlow PLM.
 * USADO EM: Peca entity, Kanban Board drag-and-drop, Dashboard por etapa.
 * ============================================================================
 */
public enum EtapaPecaEnum {
    GERACAO_FICHA,
    ENGENHARIA_RECEBIMENTO,
    MODELAGEM,
    MINI_RISCO_CONSUMO,
    CHECAGEM_MP_LINX,
    AGUARDANDO_TECIDO_FORNECEDOR,
    ESTOQUE_TECIDOS_MATRIZ,
    REVISAO_INTEGRACAO_GERACAO_OP,
    ENCAIXE_RISCO,
    CORTE,
    ESTAMPARIA,
    BORDADO,
    ESTOQUE_AVIAMENTOS,
    CD_LAVANDERIA,
    LAVANDERIA,
    ACABAMENTO,
    PRE_CUSTO_SEQUENCIA_OPERACIONAL,
    APROVACAO_PILOTO_CM,
    FINAL,
    INICIAL,
    INTEGRACAO_LINX,
    REVISAR_FICHA_TECNICA_LIBERAR_MOSTRUARIO,

    // Valores legados mantidos para que registros anteriores continuem legíveis.
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
