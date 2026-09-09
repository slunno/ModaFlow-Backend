package com.modaflow.backend.service.impl;

import com.modaflow.backend.dto.request.LoginRequestDTO;
import com.modaflow.backend.dto.response.AuthResponseDTO;
import com.modaflow.backend.dto.response.UsuarioResponseDTO;
import com.modaflow.backend.service.AuthService;
import org.springframework.stereotype.Service;

/**
 * SERVIÇO (IMPL): AuthServiceImpl
 * DESCRIÇÃO: Implementação dos fluxos de autenticação, validação de senha (BCrypt) e emissão de JWT.
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        // TODO: Implementar autenticação via Spring Security AuthenticationManager e gerar JWT
        return null;
    }

    @Override
    public UsuarioResponseDTO getUsuarioLogado() {
        // TODO: Buscar usuário autenticado a partir do SecurityContextHolder
        return null;
    }
}
