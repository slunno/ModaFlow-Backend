package com.modaflow.backend.security;

/**
 * ============================================================================
 * CLASSE: TenantContext
 * DESCRIÇÃO: Armazena o ID da marca (Tenant) ativa da requisição atual em ThreadLocal.
 * PROPÓSITO: Garantir que todas as consultas e operações do banco filtrem pela marca ativa.
 * ============================================================================
 */
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(Long marcaId) {
        CURRENT_TENANT.set(marcaId);
    }

    public static Long getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
