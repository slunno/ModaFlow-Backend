package com.modaflow.backend.service.impl;

import com.modaflow.backend.dto.response.DashboardMetricsResponseDTO;
import com.modaflow.backend.dto.response.GraficosBIResponseDTO;
import com.modaflow.backend.service.DashboardService;
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
