package com.modaflow.backend.marca.impl;

import com.modaflow.backend.common.exception.ResourceNotFoundException;

import com.modaflow.backend.marca.dto.CriarMarcaRequestDTO;
import com.modaflow.backend.marca.dto.AtualizarMarcaRequestDTO;
import com.modaflow.backend.marca.dto.MarcaResponseDTO;
import com.modaflow.backend.marca.MarcaService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SERVIÇO (IMPL): MarcaServiceImpl
 * DESCRIÇÃO: Implementação das regras de negócio de criação e edição de marcas da AKR BRANDS.
 */
@Service
public class MarcaServiceImpl implements MarcaService {

    @Override
    public List<MarcaResponseDTO> listarTodas() {
        // TODO: Buscar todas as marcas do repositório
        return List.of();
    }

    @Override
    public MarcaResponseDTO buscarPorId(Long id) {
        // TODO: Buscar marca por ID ou lançar ResourceNotFoundException
        return null;
    }

    @Override
    public MarcaResponseDTO criar(CriarMarcaRequestDTO request) {
        // TODO: Salvar nova marca no banco de dados
        return null;
    }

    @Override
    public MarcaResponseDTO atualizar(Long id, AtualizarMarcaRequestDTO request) {
        // TODO: Atualizar dados da marca existente
        return null;
    }
}
