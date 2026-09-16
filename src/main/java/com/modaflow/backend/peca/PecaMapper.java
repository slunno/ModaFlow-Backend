package com.modaflow.backend.peca;

import com.modaflow.backend.peca.dto.CriarPecaRequestDTO;
import com.modaflow.backend.peca.dto.PecaKanbanResponseDTO;
import com.modaflow.backend.peca.dto.PecaResponseDTO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MAPPER (MapStruct): PecaMapper
 * DESCRIÇÃO: Interface MapStruct para conversão de Peca entity e DTOs.
 */
@Mapper(componentModel = "spring")
public interface PecaMapper {

    PecaResponseDTO toResponseDTO(Peca peca);

    PecaKanbanResponseDTO toKanbanResponseDTO(Peca peca);

    List<PecaResponseDTO> toResponseDTOList(List<Peca> pecas);

    List<PecaKanbanResponseDTO> toKanbanResponseDTOList(List<Peca> pecas);

    Peca toEntity(CriarPecaRequestDTO dto);
}
