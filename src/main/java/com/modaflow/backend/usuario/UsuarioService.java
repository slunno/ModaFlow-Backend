package com.modaflow.backend.usuario;

import com.modaflow.backend.usuario.dto.CriarUsuarioRequestDTO;
import com.modaflow.backend.usuario.dto.AtualizarUsuarioRequestDTO;
import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;

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
