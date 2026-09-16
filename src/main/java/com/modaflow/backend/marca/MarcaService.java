package com.modaflow.backend.marca;

import com.modaflow.backend.marca.dto.CriarMarcaRequestDTO;
import com.modaflow.backend.marca.dto.AtualizarMarcaRequestDTO;
import com.modaflow.backend.marca.dto.MarcaResponseDTO;

import java.util.List;

/**
 * SERVIÇO: MarcaService
 * DESCRIÇÃO: Operações de gestão de Marcas (Tenants) da AKR BRANDS.
 */
public interface MarcaService {
    List<MarcaResponseDTO> listarTodas();
    MarcaResponseDTO buscarPorId(Long id);
    MarcaResponseDTO criar(CriarMarcaRequestDTO request);
    MarcaResponseDTO atualizar(Long id, AtualizarMarcaRequestDTO request);
}
