package com.modaflow.backend.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarcaCargoRequestDTO {
    @NotNull
    private Long marcaId;

    @NotBlank
    private String cargo;
}
