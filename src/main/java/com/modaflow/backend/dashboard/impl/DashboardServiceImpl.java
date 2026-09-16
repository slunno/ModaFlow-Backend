package com.modaflow.backend.dashboard.impl;

import com.modaflow.backend.dashboard.dto.DashboardMetricsResponseDTO;
import com.modaflow.backend.dashboard.dto.GraficosBIResponseDTO;
import com.modaflow.backend.dashboard.DashboardService;
import org.springframework.stereotype.Service;

/**
 * SERVIÇO (IMPL): DashboardServiceImpl
 * DESCRIÇÃO: Consultas agregadas (COUNT, SUM) no banco de dados para alimentar o DashboardTab e GraficosTab.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    @Override
    public DashboardMetricsResponseDTO obterMetricasPorEtapa() {
        return null;
    }

    @Override
    public GraficosBIResponseDTO obterDadosGraficosBI() {
        return null;
    }
}
