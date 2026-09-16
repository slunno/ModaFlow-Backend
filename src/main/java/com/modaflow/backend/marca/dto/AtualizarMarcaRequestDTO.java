package com.modaflow.backend.marca.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AtualizarMarcaRequestDTO {
    private String nome;
    private String code;
    private String initials;
    private String heroImageUrl;
    private String logoUrl;
    private String description;
    private String badgeTag;
}
