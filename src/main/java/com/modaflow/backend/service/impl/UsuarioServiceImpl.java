package com.modaflow.backend.service.impl;

import com.modaflow.backend.dto.request.CriarUsuarioRequestDTO;
import com.modaflow.backend.dto.request.AtualizarUsuarioRequestDTO;
import com.modaflow.backend.dto.response.UsuarioResponseDTO;
import com.modaflow.backend.service.UsuarioService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SERVIÇO (IMPL): UsuarioServiceImpl
 * DESCRIÇÃO: Lógica de negócio para criação, edição e bloqueio de usuários.
 */
@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Override
    public List<UsuarioResponseDTO> listarTodos() {
        return List.of();
    }

    @Override
    public UsuarioResponseDTO buscarPorId(Long id) {
        return null;
    }

    @Override
    public UsuarioResponseDTO criar(CriarUsuarioRequestDTO request) {
        return null;
    }

    @Override
    public UsuarioResponseDTO atualizar(Long id, AtualizarUsuarioRequestDTO request) {
        return null;
    }

    @Override
    public void alternarStatusAcesso(Long id, boolean ativo) {
    }
}
