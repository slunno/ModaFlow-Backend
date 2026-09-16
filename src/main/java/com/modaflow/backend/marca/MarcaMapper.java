package com.modaflow.backend.marca;

import com.modaflow.backend.marca.dto.CriarMarcaRequestDTO;
import com.modaflow.backend.marca.dto.MarcaResponseDTO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MAPPER (MapStruct): MarcaMapper
 * DESCRIÇÃO: Interface MapStruct para conversão de Marca entity e DTOs.
 */
@Mapper(componentModel = "spring")
public interface MarcaMapper {

    MarcaResponseDTO toResponseDTO(Marca marca);

    List<MarcaResponseDTO> toResponseDTOList(List<Marca> marcas);

    Marca toEntity(CriarMarcaRequestDTO dto);
}
