package com.modaflow.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ============================================================================
 * FILTRO: TenantInterceptorFilter
 * DESCRIÇÃO: Intercepta cada requisição HTTP para extrair o cabeçalho 'X-Marca-Id'
 *            e definir no TenantContext (ThreadLocal). Limpa ao final da requisição.
 * ============================================================================
 */
@Component
public class TenantInterceptorFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Marca-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                Long marcaId = Long.parseLong(tenantHeader);
                TenantContext.setCurrentTenant(marcaId);
            } catch (NumberFormatException ignored) {
                // Header inválido, mantém tenant nulo para ser validado na camada de serviço/security
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
