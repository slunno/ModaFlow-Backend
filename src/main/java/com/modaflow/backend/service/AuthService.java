package com.modaflow.backend.service;

import com.modaflow.backend.dto.request.LoginRequestDTO;
import com.modaflow.backend.dto.response.AuthResponseDTO;
import com.modaflow.backend.dto.response.UsuarioResponseDTO;

/**
 * SERVIÇO: AuthService
 * DESCRIÇÃO: Interface do serviço de autenticação, login JWT e obtenção do perfil logado.
 */
public interface AuthService {
    AuthResponseDTO login(LoginRequestDTO request);
    UsuarioResponseDTO getUsuarioLogado();
}
