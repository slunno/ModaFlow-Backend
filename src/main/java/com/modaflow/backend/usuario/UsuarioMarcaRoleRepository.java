package com.modaflow.backend.usuario;

import com.modaflow.backend.usuario.UsuarioMarcaRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITÓRIO: UsuarioMarcaRoleRepository
 * DESCRIÇÃO: Operações de associação entre Usuários, Marcas e Cargos.
 */
@Repository
public interface UsuarioMarcaRoleRepository extends JpaRepository<UsuarioMarcaRole, Long> {
    @EntityGraph(attributePaths = "marca")
    List<UsuarioMarcaRole> findByUsuarioId(Long usuarioId);
    List<UsuarioMarcaRole> findByMarcaId(Long marcaId);
    boolean existsByUsuarioIdAndMarcaId(Long usuarioId, Long marcaId);
}
