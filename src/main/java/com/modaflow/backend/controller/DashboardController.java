package com.modaflow.backend.controller;

import com.modaflow.backend.dto.response.DashboardMetricsResponseDTO;
import com.modaflow.backend.dto.response.GraficosBIResponseDTO;
import com.modaflow.backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CONTROLLER: DashboardController
 * ENDPOINT BASE: /dashboard
 * DESCRIÇÃO: Retorna métricas consolidadas por etapa e agregados para relatórios e gráficos de BI.
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard & BI", description = "Métricas agregadas para dashboards e gráficos de Business Intelligence")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/metrics")
    @Operation(summary = "Obter métricas agregadas por etapa de produção")
    public ResponseEntity<DashboardMetricsResponseDTO> obterMetricasPorEtapa() {
        return ResponseEntity.ok(dashboardService.obterMetricasPorEtapa());
    }

    @GetMapping("/graficos")
    @Operation(summary = "Obter séries temporais e dados agregados para os gráficos de BI")
    public ResponseEntity<GraficosBIResponseDTO> obterDadosGraficosBI() {
        return ResponseEntity.ok(dashboardService.obterDadosGraficosBI());
    }
}
