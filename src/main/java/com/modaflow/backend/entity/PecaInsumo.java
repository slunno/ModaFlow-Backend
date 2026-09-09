package com.modaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * ============================================================================
 * ENTIDADE: PecaInsumo (Ficha Técnica)
 * DESCRIÇÃO: Tabela de associação N:N entre Peça e Insumo (quantidade consumida e custo).
 * TABELA: peca_insumos
 * ============================================================================
 */
@Entity
@Table(name = "peca_insumos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PecaInsumo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "peca_id", nullable = false)
    private Peca peca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Column(precision = 10, scale = 3, nullable = false)
    private BigDecimal consumo; // Ex: 1.45 metros

    @Column(precision = 10, scale = 2)
    private BigDecimal custoCalculado;
}
