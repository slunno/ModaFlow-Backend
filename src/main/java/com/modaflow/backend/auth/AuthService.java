package com.modaflow.backend.auth;

import com.modaflow.backend.auth.dto.LoginRequestDTO;
import com.modaflow.backend.auth.dto.AuthResponseDTO;
import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;

/**
 * SERVIÇO: AuthService
 * DESCRIÇÃO: Interface do serviço de autenticação, login JWT e obtenção do perfil logado.
 */
public interface AuthService {
    AuthResponseDTO login(LoginRequestDTO request);
    UsuarioResponseDTO getUsuarioLogado();
}
