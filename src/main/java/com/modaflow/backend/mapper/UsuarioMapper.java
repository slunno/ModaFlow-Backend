package com.modaflow.backend.mapper;

import com.modaflow.backend.dto.response.UsuarioResponseDTO;
import com.modaflow.backend.entity.Usuario;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * MAPPER: UsuarioMapper
 * DESCRIÇÃO: Converte a entidade Usuario em UsuarioResponseDTO sem expor o campo de senha.
 */
@Component
public class UsuarioMapper {

    public UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        if (usuario == null) return null;

        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setId(usuario.getId());
        dto.setNome(usuario.getNome());
        dto.setEmail(usuario.getEmail());
        dto.setEmpresa(usuario.getEmpresa());
        dto.setCargo(usuario.getCargo());
        dto.setAvatarUrl(usuario.getAvatarUrl());
        dto.setAtivo(usuario.getAtivo());
        dto.setMarcasPermitidas(Collections.emptyList());
        return dto;
    }

    public List<UsuarioResponseDTO> toResponseDTOList(List<Usuario> usuarios) {
        if (usuarios == null) return Collections.emptyList();
        return usuarios.stream().map(this::toResponseDTO).toList();
    }
}
