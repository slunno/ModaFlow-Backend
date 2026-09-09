package com.modaflow.backend.entity;

import com.modaflow.backend.enums.StatusColecaoEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ENTIDADE: Colecao
 * DESCRIÇÃO: Coleção de moda (ex: Verão 2026, Alto Inverno, Casual Black).
 * TABELA: colecoes
 * ============================================================================
 */
@Entity
@Table(name = "colecoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Colecao extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusColecaoEnum status;

    @Column(name = "concluido_em_date")
    private LocalDate concluidoEmDate;

    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;

    @OneToMany(mappedBy = "colecao", cascade = CascadeType.ALL)
    private List<Peca> pecas = new ArrayList<>();
}
