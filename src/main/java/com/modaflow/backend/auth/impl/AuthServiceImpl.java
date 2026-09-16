package com.modaflow.backend.auth.impl;

import com.modaflow.backend.auth.dto.LoginRequestDTO;
import com.modaflow.backend.auth.dto.AuthResponseDTO;
import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;
import com.modaflow.backend.usuario.Usuario;
import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.usuario.UsuarioMapper;
import com.modaflow.backend.usuario.InMemoryUserRepository;
import com.modaflow.backend.security.JwtTokenProvider;
import com.modaflow.backend.auth.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * SERVIÇO (IMPL): AuthServiceImpl
 * DESCRIÇÃO: Autentica o usuário comparando a senha informada com o Hash BCrypt em memória
 *            e emite o token JWT.
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private UsuarioMapper usuarioMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Credenciais inválidas: usuário não encontrado."));

        if (!usuario.getAtivo()) {
            throw new BusinessRuleException("Acesso negado: a conta deste usuário está desativada.");
        }

        // 🔒 Valida a senha digitada contra o Hash BCrypt armazenado em memória
        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
            throw new BusinessRuleException("Credenciais inválidas: senha incorreta.");
        }

        String token = jwtTokenProvider.generateToken(usuario.getEmail());
        UsuarioResponseDTO responseDTO = usuarioMapper.toResponseDTO(usuario);
        return new AuthResponseDTO(token, responseDTO);
    }

    @Override
    public UsuarioResponseDTO getUsuarioLogado() {
        // Retorna o usuário padrão logado (ou o primeiro usuário ativo)
        Usuario usuario = userRepository.findByEmail("admin@akrbrands.com.br")
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
        return usuarioMapper.toResponseDTO(usuario);
    }
}
