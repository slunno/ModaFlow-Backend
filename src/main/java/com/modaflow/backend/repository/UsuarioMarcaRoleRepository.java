package com.modaflow.backend.repository;

import com.modaflow.backend.entity.UsuarioMarcaRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITÓRIO: UsuarioMarcaRoleRepository
 * DESCRIÇÃO: Operações de associação entre Usuários, Marcas e Cargos.
 */
@Repository
public interface UsuarioMarcaRoleRepository extends JpaRepository<UsuarioMarcaRole, Long> {
    List<UsuarioMarcaRole> findByUsuarioId(Long usuarioId);
    List<UsuarioMarcaRole> findByMarcaId(Long marcaId);
}
