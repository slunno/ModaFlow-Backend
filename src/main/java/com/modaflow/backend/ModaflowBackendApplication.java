package com.modaflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * ============================================================================
 * MÓDULO: Aplicação Principal
 * CLASSE: ModaflowBackendApplication
 * PROJETO: ModaFlow PLM — AKR BRANDS
 * DESCRIÇÃO: Ponto de entrada (main class) da API REST backend Spring Boot.
 *            Inicializa os contextos do Spring Boot, auditoria JPA e segurança.
 * ============================================================================
 */
@SpringBootApplication
@EnableJpaAuditing
public class ModaflowBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModaflowBackendApplication.class, args);
    }
}
