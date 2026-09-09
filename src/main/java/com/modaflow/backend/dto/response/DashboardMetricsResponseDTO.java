package com.modaflow.backend.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * DTO: DashboardMetricsResponseDTO
 * DESCRIÇÃO: Métricas quantitativas agregadas por etapa e totais de peças/coleções.
 */
@Getter
@Setter
public class DashboardMetricsResponseDTO {
    private long totalPecas;
    private long totalColecoes;
    private long pecasAtrasadas;
    private Map<String, Long> pecasPorEtapa;
}
