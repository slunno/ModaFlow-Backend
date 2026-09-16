package com.modaflow.backend.insumo;

import com.modaflow.backend.marca.Marca;

import com.modaflow.backend.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * ============================================================================
 * ENTIDADE: Caracteristica
 * DESCRIÇÃO: Atributos customizáveis de produto vinculados à marca.
 * TABELA: caracteristicas
 * ============================================================================
 */
@Entity
@Table(name = "caracteristicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caracteristica extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;
}
