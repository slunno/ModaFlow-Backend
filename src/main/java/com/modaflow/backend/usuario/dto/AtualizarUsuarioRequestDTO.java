package com.modaflow.backend.usuario.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AtualizarUsuarioRequestDTO {
    private String nome;
    private String empresa;
    private String cargo;
}
