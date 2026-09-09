package com.modaflow.backend.service;

import com.modaflow.backend.dto.response.DashboardMetricsResponseDTO;
import com.modaflow.backend.dto.response.GraficosBIResponseDTO;

/**
 * SERVIÇO: DashboardService
 * DESCRIÇÃO: Agregação de dados para métricas por etapa e gráficos do BI.
 */
public interface DashboardService {
    DashboardMetricsResponseDTO obterMetricasPorEtapa();
    GraficosBIResponseDTO obterDadosGraficosBI();
}
