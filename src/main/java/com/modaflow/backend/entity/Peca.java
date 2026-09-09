package com.modaflow.backend.entity;

import com.modaflow.backend.enums.EtapaPecaEnum;
import com.modaflow.backend.enums.StatusPecaEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ENTIDADE: Peca
 * DESCRIÇÃO: Peça de vestuário gerenciada no PLM.
 * TABELA: pecas
 * ============================================================================
 */
@Entity
@Table(name = "pecas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Peca extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 100)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPecaEnum status;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_atual", nullable = false, length = 50)
    private EtapaPecaEnum etapaAtual;

    @Column(length = 100)
    private String tema;

    @Column(length = 100)
    private String estilista;

    @Column(length = 100)
    private String modelista;

    @Column(name = "imagem_croqui_url")
    private String imagemCroquiUrl;

    @Column(precision = 10, scale = 2)
    private BigDecimal custo;

    @Column(precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "previsao_entrega")
    private LocalDate previsaoEntrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "colecao_id", nullable = false)
    private Colecao colecao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;

    @OneToMany(mappedBy = "peca", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PecaInsumo> insumos = new ArrayList<>();
}
