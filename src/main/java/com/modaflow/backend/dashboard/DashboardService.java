package com.modaflow.backend.dashboard;

import com.modaflow.backend.dashboard.dto.DashboardMetricsResponseDTO;
import com.modaflow.backend.dashboard.dto.GraficosBIResponseDTO;

/**
 * SERVIÇO: DashboardService
 * DESCRIÇÃO: Agregação de dados para métricas por etapa e gráficos do BI.
 */
public interface DashboardService {
    DashboardMetricsResponseDTO obterMetricasPorEtapa();
    GraficosBIResponseDTO obterDadosGraficosBI();
}
