package com.modaflow.backend.service;

import com.modaflow.backend.dto.request.CriarMarcaRequestDTO;
import com.modaflow.backend.dto.request.AtualizarMarcaRequestDTO;
import com.modaflow.backend.dto.response.MarcaResponseDTO;

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
