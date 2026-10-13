package com.modaflow.backend.usuario.dto;

import com.modaflow.backend.marca.dto.MarcaResponseDTO;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO: UsuarioResponseDTO
 * DESCRIÇÃO: Representação de um usuário retornada à API (sem campo de senha).
 */
@Getter
@Setter
public class UsuarioResponseDTO {
    private Long id;
    private String nome;
    private String email;
    private String empresa;
    private String cargo;
    private String avatarUrl;
    private Boolean ativo;
    private List<MarcaResponseDTO> marcasPermitidas;
    private List<MarcaCargoResponseDTO> brandRoles;
}
