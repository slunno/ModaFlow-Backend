package com.modaflow.backend.insumo;

import com.modaflow.backend.insumo.dto.CriarInsumoRequestDTO;
import com.modaflow.backend.insumo.dto.InsumoResponseDTO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MAPPER (MapStruct): InsumoMapper
 * DESCRIÇÃO: Interface MapStruct para conversão de Insumo entity e DTOs.
 */
@Mapper(componentModel = "spring")
public interface InsumoMapper {

    InsumoResponseDTO toResponseDTO(Insumo insumo);

    List<InsumoResponseDTO> toResponseDTOList(List<Insumo> insumos);

    Insumo toEntity(CriarInsumoRequestDTO dto);
}
