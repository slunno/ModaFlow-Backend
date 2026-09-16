package com.modaflow.backend.usuario;

import com.modaflow.backend.marca.Marca;

import com.modaflow.backend.common.entity.BaseEntity;

import com.modaflow.backend.usuario.CargoEnum;
import jakarta.persistence.*;
import lombok.*;

/**
 * ============================================================================
 * ENTIDADE: UsuarioMarcaRole
 * DESCRIÇÃO: Tabela de associação N:N entre Usuario e Marca, definindo o cargo/permissão.
 * TABELA: usuario_marca_roles
 * ============================================================================
 */
@Entity
@Table(name = "usuario_marca_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioMarcaRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CargoEnum cargo;
}
