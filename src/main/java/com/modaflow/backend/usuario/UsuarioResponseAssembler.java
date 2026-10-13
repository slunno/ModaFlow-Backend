package com.modaflow.backend.usuario;

import com.modaflow.backend.marca.MarcaMapper;
import com.modaflow.backend.usuario.dto.MarcaCargoResponseDTO;
import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;
import org.springframework.stereotype.Component;

/** Monta o usuário com os vínculos de marca efetivamente salvos no banco. */
@Component
public class UsuarioResponseAssembler {
    private final UsuarioMapper usuarios;
    private final UsuarioMarcaRoleRepository roles;
    private final MarcaMapper marcas;

    public UsuarioResponseAssembler(UsuarioMapper usuarios, UsuarioMarcaRoleRepository roles, MarcaMapper marcas) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.marcas = marcas;
    }

    public UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        UsuarioResponseDTO response = usuarios.toResponseDTO(usuario);
        var vinculos = roles.findByUsuarioId(usuario.getId());
        response.setMarcasPermitidas(vinculos.stream().map(role -> marcas.toResponseDTO(role.getMarca())).toList());
        response.setBrandRoles(vinculos.stream()
                .map(role -> new MarcaCargoResponseDTO(role.getMarca().getId(), role.getCargo().name()))
                .toList());
        return response;
    }
}
