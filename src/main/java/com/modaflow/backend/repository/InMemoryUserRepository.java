package com.modaflow.backend.repository;

import com.modaflow.backend.entity.Usuario;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 * REPOSITÓRIO EM MEMÓRIA: InMemoryUserRepository
 * DESCRIÇÃO: Armazena os usuários em memória (Thread-Safe ConcurrentHashMap)
 *            sem utilizar tabelas do banco de dados relacional.
 *            Criptografa todas as senhas utilizando Hash BCrypt.
 * ============================================================================
 */
@Repository
public class InMemoryUserRepository {

    // Estrutura em memória chaveada pelo E-mail
    private final Map<String, Usuario> usuariosMap = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Inicializa os usuários padrão na memória assim que a aplicação é iniciada.
     */
    @PostConstruct
    public void initDefaultUsers() {
        // Usuario 1: Admin
        Usuario admin = Usuario.builder()
                .nome("Administrador AKR")
                .email("admin@akrbrands.com.br")
                .senha(passwordEncoder.encode("admin123")) // Criptografado com Hash BCrypt
                .empresa("AKR BRANDS")
                .cargo("ADMIN")
                .ativo(true)
                .marcasRoles(new ArrayList<>())
                .build();
        admin.setId(idGenerator.getAndIncrement());
        admin.setCreatedAt(LocalDateTime.now());
        usuariosMap.put(admin.getEmail().toLowerCase(), admin);

        // Usuario 2: Gestor Juliano
        Usuario juliano = Usuario.builder()
                .nome("Juliano")
                .email("juliano@akrbrands.com.br")
                .senha(passwordEncoder.encode("modaflow2026")) // Criptografado com Hash BCrypt
                .empresa("King & Joe")
                .cargo("GESTOR")
                .ativo(true)
                .marcasRoles(new ArrayList<>())
                .build();
        juliano.setId(idGenerator.getAndIncrement());
        juliano.setCreatedAt(LocalDateTime.now());
        usuariosMap.put(juliano.getEmail().toLowerCase(), juliano);
    }

    public Optional<Usuario> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return Optional.ofNullable(usuariosMap.get(email.toLowerCase()));
    }

    public Optional<Usuario> findById(Long id) {
        return usuariosMap.values().stream()
                .filter(u -> Objects.equals(u.getId(), id))
                .findFirst();
    }

    public List<Usuario> findAll() {
        return new ArrayList<>(usuariosMap.values());
    }

    public boolean existsByEmail(String email) {
        if (email == null) return false;
        return usuariosMap.containsKey(email.toLowerCase());
    }

    public Usuario save(Usuario usuario) {
        if (usuario.getId() == null) {
            usuario.setId(idGenerator.getAndIncrement());
            usuario.setCreatedAt(LocalDateTime.now());
        }
        usuario.setUpdatedAt(LocalDateTime.now());
        usuariosMap.put(usuario.getEmail().toLowerCase(), usuario);
        return usuario;
    }
}
