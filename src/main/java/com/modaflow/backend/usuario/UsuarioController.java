package com.modaflow.backend.usuario;

import com.modaflow.backend.usuario.dto.CriarUsuarioRequestDTO;
import com.modaflow.backend.usuario.dto.AtualizarUsuarioRequestDTO;
import com.modaflow.backend.usuario.dto.UsuarioResponseDTO;
import com.modaflow.backend.usuario.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CONTROLLER: UsuarioController
 * ENDPOINT BASE: /usuarios
 */
@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de contas de usuário")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar todos os usuários")
    public ResponseEntity<List<UsuarioResponseDTO>> listarTodos() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar usuário por ID")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo usuário")
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody CriarUsuarioRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.criar(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar perfil de usuário existente")
    public ResponseEntity<UsuarioResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuarioRequestDTO request) {
        return ResponseEntity.ok(usuarioService.atualizar(id, request));
    }

    @PatchMapping("/{id}/acesso")
    @Operation(summary = "Bloquear ou ativar acesso do usuário")
    public ResponseEntity<Void> alternarAcesso(@PathVariable Long id, @RequestParam boolean ativo) {
        usuarioService.alternarStatusAcesso(id, ativo);
        return ResponseEntity.noContent().build();
    }
}
