package com.modaflow.backend.usuario;

import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MAPPER (MapStruct): UsuarioMapper
 * DESCRIÇÃO: Interface MapStruct para conversão de Usuario entity em UsuarioResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "marcasPermitidas", ignore = true)
    @Mapping(target = "brandRoles", ignore = true)
    UsuarioResponseDTO toResponseDTO(Usuario usuario);

    List<UsuarioResponseDTO> toResponseDTOList(List<Usuario> usuarios);
}
