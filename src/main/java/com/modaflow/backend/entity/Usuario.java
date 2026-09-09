package com.modaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ENTIDADE: Usuario
 * DESCRIÇÃO: Representa um usuário autenticável do ModaFlow PLM (AKR BRANDS).
 * TABELA: usuarios
 * RELACIONAMENTOS: N:N com Marca através de UsuarioMarcaRole.
 * ============================================================================
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(length = 100)
    private String empresa;

    @Column(length = 100)
    private String cargo;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Builder.Default
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UsuarioMarcaRole> marcasRoles = new ArrayList<>();
}
