package com.modaflow.backend.usuario.impl;

import com.modaflow.backend.usuario.dto.CriarUsuarioRequestDTO;
import com.modaflow.backend.usuario.dto.AtualizarUsuarioRequestDTO;
import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;
import com.modaflow.backend.usuario.Usuario;
import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.usuario.UsuarioMapper;
import com.modaflow.backend.usuario.UsuarioRepository;
import com.modaflow.backend.usuario.UsuarioService;
import com.modaflow.backend.usuario.UsuarioResponseAssembler;
import com.modaflow.backend.usuario.UsuarioMarcaRole;
import com.modaflow.backend.usuario.UsuarioMarcaRoleRepository;
import com.modaflow.backend.usuario.CargoEnum;
import com.modaflow.backend.usuario.dto.MarcaCargoRequestDTO;
import com.modaflow.backend.marca.MarcaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.modaflow.backend.security.UserPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;

/**
 * SERVIÇO (IMPL): UsuarioServiceImpl
 * DESCRIÇÃO: Gerencia o cadastro, edição, listagem e bloqueio de usuários em memória (Hash BCrypt).
 */
@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Autowired
    private UsuarioRepository userRepository;

    @Autowired
    private UsuarioMapper usuarioMapper;

    @Autowired private UsuarioResponseAssembler responseAssembler;
    @Autowired private UsuarioMarcaRoleRepository roleRepository;
    @Autowired private MarcaRepository marcaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public List<UsuarioResponseDTO> listarTodos() {
        List<Usuario> usuarios = userRepository.findAll();
        return usuarios.stream().map(responseAssembler::toResponseDTO).toList();
    }

    @Override
    public UsuarioResponseDTO buscarPorId(Long id) {
        Usuario usuario = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));
        return responseAssembler.toResponseDTO(usuario);
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
        return responseAssembler.toResponseDTO(salvo);
    }

    @Override
    @Transactional
    public UsuarioResponseDTO atualizar(Long id, AtualizarUsuarioRequestDTO request) {
        Usuario usuario = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));

        if (request.getNome() != null) usuario.setNome(request.getNome());
        if (request.getEmpresa() != null) usuario.setEmpresa(request.getEmpresa());
        if (request.getCargo() != null) usuario.setCargo(request.getCargo());

        Usuario atualizado = userRepository.save(usuario);
        if (request.getBrandRoles() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (!(principal instanceof UserPrincipal actor) || roleRepository.findByUsuarioId(actor.getId())
                    .stream().noneMatch(role -> role.getCargo() == CargoEnum.ADMIN)) {
                throw new AccessDeniedException("Somente administradores podem alterar os cargos por marca.");
            }
            var existentes = roleRepository.findByUsuarioId(id);
            var idsRecebidos = new HashSet<Long>();
            for (MarcaCargoRequestDTO entrada : request.getBrandRoles()) {
                if (!idsRecebidos.add(entrada.getMarcaId())) {
                    throw new BusinessRuleException("Marca informada mais de uma vez: " + entrada.getMarcaId());
                }
                var marca = marcaRepository.findById(entrada.getMarcaId())
                        .orElseThrow(() -> new ResourceNotFoundException("Marca não encontrada com ID: " + entrada.getMarcaId()));
                CargoEnum cargo;
                try {
                    cargo = CargoEnum.valueOf(entrada.getCargo().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new BusinessRuleException("Cargo inválido: " + entrada.getCargo());
                }
                var vinculo = existentes.stream().filter(role -> role.getMarca().getId().equals(marca.getId())).findFirst()
                        .orElseGet(() -> UsuarioMarcaRole.builder().usuario(usuario).marca(marca).build());
                vinculo.setCargo(cargo);
                roleRepository.save(vinculo);
            }
            roleRepository.deleteAll(existentes.stream()
                    .filter(role -> !idsRecebidos.contains(role.getMarca().getId())).toList());
            roleRepository.flush();
        }
        return responseAssembler.toResponseDTO(atualizado);
    }

    @Override
    public void alternarStatusAcesso(Long id, boolean ativo) {
        Usuario usuario = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));

        usuario.setAtivo(ativo);
        userRepository.save(usuario);
    }
}
