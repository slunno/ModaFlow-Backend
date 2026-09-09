package com.modaflow.backend.service.impl;

import com.modaflow.backend.dto.request.CriarInsumoRequestDTO;
import com.modaflow.backend.dto.request.AtualizarInsumoRequestDTO;
import com.modaflow.backend.dto.response.InsumoResponseDTO;
import com.modaflow.backend.enums.TipoInsumoEnum;
import com.modaflow.backend.service.InsumoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * SERVIÇO (IMPL): InsumoServiceImpl
 * DESCRIÇÃO: Implementação das regras de negócio para Insumos (Tecidos e Aviamentos).
 */
@Service
public class InsumoServiceImpl implements InsumoService {

    @Override
    public Page<InsumoResponseDTO> listarPorTipo(TipoInsumoEnum tipo, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public InsumoResponseDTO buscarPorId(Long id) {
        return null;
    }

    @Override
    public InsumoResponseDTO criar(CriarInsumoRequestDTO request) {
        return null;
    }

    @Override
    public InsumoResponseDTO atualizar(Long id, AtualizarInsumoRequestDTO request) {
        return null;
    }

    @Override
    public void excluir(Long id) {
    }

    @Override
    public int importarLote(MultipartFile file, TipoInsumoEnum tipo) {
        return 0;
    }
}
