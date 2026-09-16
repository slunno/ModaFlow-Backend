package com.modaflow.backend.common.exception;

import com.modaflow.backend.marca.Marca;

/**
 * EXCEÇÃO: TenantAccessDeniedException
 * DESCRIÇÃO: Lançada quando o usuário tenta acessar dados de uma Marca (Tenant) à qual não possui permissão.
 */
public class TenantAccessDeniedException extends RuntimeException {
    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
