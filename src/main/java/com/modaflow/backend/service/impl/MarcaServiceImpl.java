package com.modaflow.backend.service.impl;

import com.modaflow.backend.dto.request.CriarMarcaRequestDTO;
import com.modaflow.backend.dto.request.AtualizarMarcaRequestDTO;
import com.modaflow.backend.dto.response.MarcaResponseDTO;
import com.modaflow.backend.service.MarcaService;
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
