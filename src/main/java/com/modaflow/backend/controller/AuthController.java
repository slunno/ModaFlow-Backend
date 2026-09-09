package com.modaflow.backend.controller;

import com.modaflow.backend.dto.request.LoginRequestDTO;
import com.modaflow.backend.dto.response.AuthResponseDTO;
import com.modaflow.backend.dto.response.UsuarioResponseDTO;
import com.modaflow.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CONTROLLER: AuthController
 * ENDPOINT BASE: /auth
 * DESCRIÇÃO: Processa login do usuário, emissão de JWT e identificação do perfil autenticado.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Endpoints de login e perfil do usuário")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuário e emitir token JWT")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Obter dados do usuário logado e marcas permitidas")
    public ResponseEntity<UsuarioResponseDTO> getMe() {
        return ResponseEntity.ok(authService.getUsuarioLogado());
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidar sessão / logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
