package com.modaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CriarMarcaRequestDTO {
    @NotBlank(message = "O nome da marca é obrigatório")
    private String nome;
    @NotBlank(message = "O código da marca é obrigatório")
    private String code;
    private String initials;
    private String heroImageUrl;
    private String logoUrl;
    private String description;
    private String badgeTag;
}
