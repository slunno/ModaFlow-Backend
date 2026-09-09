package com.modaflow.backend.entity;

import com.modaflow.backend.enums.TipoInsumoEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * ============================================================================
 * ENTIDADE: Insumo
 * DESCRIÇÃO: Unifica Tecidos e Aviamentos utilizados nas coleções/peças.
 * TABELA: insumos
 * ============================================================================
 */
@Entity
@Table(name = "insumos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insumo extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoInsumoEnum tipo; // TECIDO ou AVIAMENTO

    @Column(name = "tem_erp", nullable = false)
    private Boolean temErp;

    @Column(name = "imagem_url")
    private String imagemUrl;

    @Column(length = 150)
    private String fornecedores;

    @Column(precision = 10, scale = 2)
    private BigDecimal custo;

    @Column(length = 20)
    private String unidade; // m, kg, un, etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;
}
