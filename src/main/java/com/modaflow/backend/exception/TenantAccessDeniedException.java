package com.modaflow.backend.exception;

/**
 * EXCEÇÃO: TenantAccessDeniedException
 * DESCRIÇÃO: Lançada quando o usuário tenta acessar dados de uma Marca (Tenant) à qual não possui permissão.
 */
public class TenantAccessDeniedException extends RuntimeException {
    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
