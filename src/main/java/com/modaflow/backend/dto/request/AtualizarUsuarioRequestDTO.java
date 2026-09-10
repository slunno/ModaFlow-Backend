package com.modaflow.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AtualizarUsuarioRequestDTO {
    private String nome;
    private String empresa;
    private String cargo;
}
