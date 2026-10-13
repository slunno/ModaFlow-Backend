package com.modaflow.backend.auth.impl;

import com.modaflow.backend.auth.dto.LoginRequestDTO;
import com.modaflow.backend.auth.dto.AuthResponseDTO;
import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;
import com.modaflow.backend.usuario.Usuario;
import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.usuario.UsuarioMapper;
import com.modaflow.backend.usuario.UsuarioResponseAssembler;
import com.modaflow.backend.usuario.UsuarioRepository;
import com.modaflow.backend.security.JwtTokenProvider;
import com.modaflow.backend.auth.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import com.modaflow.backend.security.UserPrincipal;
import org.springframework.stereotype.Service;

/**
 * SERVIÇO (IMPL): AuthServiceImpl
 * DESCRIÇÃO: Autentica o usuário comparando a senha informada com o Hash BCrypt em memória
 *            e emite o token JWT.
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UsuarioRepository userRepository;

    @Autowired
    private UsuarioMapper usuarioMapper;

    @Autowired
    private UsuarioResponseAssembler responseAssembler;

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
        UsuarioResponseDTO responseDTO = responseAssembler.toResponseDTO(usuario);
        return new AuthResponseDTO(token, responseDTO);
    }

    @Override
    public UsuarioResponseDTO getUsuarioLogado() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new ResourceNotFoundException("Usuário autenticado não encontrado.");
        }
        Usuario usuario = userRepository.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado."));
        return responseAssembler.toResponseDTO(usuario);
    }
}
