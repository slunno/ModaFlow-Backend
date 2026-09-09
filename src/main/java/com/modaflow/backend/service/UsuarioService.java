package com.modaflow.backend.service;

import com.modaflow.backend.dto.request.CriarUsuarioRequestDTO;
import com.modaflow.backend.dto.request.AtualizarUsuarioRequestDTO;
import com.modaflow.backend.dto.response.UsuarioResponseDTO;

import java.util.List;

/**
 * SERVIÇO: UsuarioService
 * DESCRIÇÃO: Operações de CRUD e gestão de acessos de Usuários.
 */
public interface UsuarioService {
    List<UsuarioResponseDTO> listarTodos();
    UsuarioResponseDTO buscarPorId(Long id);
    UsuarioResponseDTO criar(CriarUsuarioRequestDTO request);
    UsuarioResponseDTO atualizar(Long id, AtualizarUsuarioRequestDTO request);
    void alternarStatusAcesso(Long id, boolean ativo);
}
