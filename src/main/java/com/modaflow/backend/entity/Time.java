package com.modaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * ============================================================================
 * ENTIDADE: Time
 * DESCRIÇÃO: Times de trabalho/criação vinculados a uma marca (Ex: Estilo, Modelagem).
 * TABELA: times
 * ============================================================================
 */
@Entity
@Table(name = "times")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Time extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "designado_produto")
    private String designadoProduto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;
}
