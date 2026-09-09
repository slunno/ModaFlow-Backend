package com.modaflow.backend.service.impl;

import com.modaflow.backend.dto.request.CriarUsuarioRequestDTO;
import com.modaflow.backend.dto.request.AtualizarUsuarioRequestDTO;
import com.modaflow.backend.dto.response.UsuarioResponseDTO;
import com.modaflow.backend.entity.Usuario;
import com.modaflow.backend.exception.BusinessRuleException;
import com.modaflow.backend.exception.ResourceNotFoundException;
import com.modaflow.backend.mapper.UsuarioMapper;
import com.modaflow.backend.repository.InMemoryUserRepository;
import com.modaflow.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SERVIÇO (IMPL): UsuarioServiceImpl
 * DESCRIÇÃO: Gerencia o cadastro, edição, listagem e bloqueio de usuários em memória (Hash BCrypt).
 */
@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private UsuarioMapper usuarioMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public List<UsuarioResponseDTO> listarTodos() {
        List<Usuario> usuarios = userRepository.findAll();
        return usuarioMapper.toResponseDTOList(usuarios);
    }

    @Override
    public UsuarioResponseDTO buscarPorId(Long id) {
        Usuario usuario = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));
        return usuarioMapper.toResponseDTO(usuario);
    }

    @Override
    public UsuarioResponseDTO criar(CriarUsuarioRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessRuleException("O e-mail informado já está cadastrado.");
        }

        Usuario novoUsuario = Usuario.builder()
                .nome(request.getNome())
                .email(request.getEmail())
                .senha(passwordEncoder.encode(request.getSenha())) // 🔒 Criptografado com Hash BCrypt
                .empresa(request.getEmpresa())
                .cargo(request.getCargo())
                .ativo(true)
                .marcasRoles(new ArrayList<>())
                .build();

        Usuario salvo = userRepository.save(novoUsuario);
        return usuarioMapper.toResponseDTO(salvo);
    }

    @Override
    public UsuarioResponseDTO atualizar(Long id, AtualizarUsuarioRequestDTO request) {
        Usuario usuario = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));

        usuario.setNome(request.getNome());
        if (request.getEmpresa() != null) usuario.setEmpresa(request.getEmpresa());
        if (request.getCargo() != null) usuario.setCargo(request.getCargo());

        Usuario atualizado = userRepository.save(usuario);
        return usuarioMapper.toResponseDTO(atualizado);
    }

    @Override
    public void alternarStatusAcesso(Long id, boolean ativo) {
        Usuario usuario = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));

        usuario.setAtivo(ativo);
        userRepository.save(usuario);
    }
}
