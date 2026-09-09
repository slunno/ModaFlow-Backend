package com.modaflow.backend.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO: MarcaResponseDTO
 * DESCRIÇÃO: Representação de uma Marca (Tenant) com dados calculados colecoesCount e pecasCount.
 */
@Getter
@Setter
public class MarcaResponseDTO {
    private Long id;
    private String nome;
    private String code;
    private String initials;
    private String heroImageUrl;
    private String logoUrl;
    private String description;
    private String badgeTag;
    private Integer colecoesCount;
    private Integer pecasCount;
}
