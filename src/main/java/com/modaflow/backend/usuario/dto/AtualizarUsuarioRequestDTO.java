package com.modaflow.backend.usuario.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.Valid;
import java.util.List;

@Getter
@Setter
public class AtualizarUsuarioRequestDTO {
    private String nome;
    private String empresa;
    private String cargo;
    @Valid
    private List<MarcaCargoRequestDTO> brandRoles;
}
