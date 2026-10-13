package com.modaflow.backend.security;

import com.modaflow.backend.common.exception.TenantAccessDeniedException;
import com.modaflow.backend.usuario.UsuarioMarcaRoleRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantAccessValidator {
    private final UsuarioMarcaRoleRepository roles;

    public TenantAccessValidator(UsuarioMarcaRoleRepository roles) { this.roles = roles; }

    public Long requireTenantForCurrentUser() {
        Long tenantId = TenantContext.getCurrentTenant();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (tenantId == null || !(principal instanceof UserPrincipal user)
                || !roles.existsByUsuarioIdAndMarcaId(user.getId(), tenantId)) {
            throw new TenantAccessDeniedException("Usuário não possui acesso à marca informada.");
        }
        return tenantId;
    }
}
