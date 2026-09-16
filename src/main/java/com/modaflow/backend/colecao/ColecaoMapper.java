package com.modaflow.backend.colecao;

import com.modaflow.backend.colecao.dto.ColecaoDetalheResponseDTO;
import com.modaflow.backend.colecao.dto.ColecaoResponseDTO;
import com.modaflow.backend.colecao.dto.CriarColecaoRequestDTO;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.List;

/**
 * MAPPER (MapStruct): ColecaoMapper
 * DESCRIÇÃO: Interface MapStruct para conversão de Colecao entity e DTOs.
 */
@Mapper(componentModel = "spring")
public interface ColecaoMapper {

    @Named("toResponseDTO")
    ColecaoResponseDTO toResponseDTO(Colecao colecao);

    @Named("toDetalheResponseDTO")
    ColecaoDetalheResponseDTO toDetalheResponseDTO(Colecao colecao);

    @IterableMapping(qualifiedByName = "toResponseDTO")
    List<ColecaoResponseDTO> toResponseDTOList(List<Colecao> colecoes);

    Colecao toEntity(CriarColecaoRequestDTO dto);
}
