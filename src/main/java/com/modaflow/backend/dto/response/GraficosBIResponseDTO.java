package com.modaflow.backend.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO: GraficosBIResponseDTO
 * DESCRIÇÃO: Séries temporais e distribuições percentuais para exibição na aba GraficosTab do Relatórios.
 */
@Getter
@Setter
public class GraficosBIResponseDTO {
    private List<Map<String, Object>> evolucaoProducaoMensal;
    private Map<String, Double> distribuicaoCustoPorCategoria;
}
