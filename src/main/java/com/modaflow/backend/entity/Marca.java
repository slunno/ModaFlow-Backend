package com.modaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ENTIDADE: Marca (Tenant)
 * DESCRIÇÃO: Representa uma marca comercial (Ex: King & Joe, K&J Black, King & Joe Play).
 * TABELA: marcas
 * IMPORTÂNCIA: É o Tenant da arquitetura Multi-Tenant isolando dados por marca.
 * ============================================================================
 */
@Entity
@Table(name = "marcas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marca extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 10)
    private String initials;

    @Column(name = "hero_image_url")
    private String heroImageUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "badge_tag", length = 50)
    private String badgeTag;

    @OneToMany(mappedBy = "marca", cascade = CascadeType.ALL)
    private List<Time> times = new ArrayList<>();

    @OneToMany(mappedBy = "marca", cascade = CascadeType.ALL)
    private List<Colecao> colecoes = new ArrayList<>();
}
