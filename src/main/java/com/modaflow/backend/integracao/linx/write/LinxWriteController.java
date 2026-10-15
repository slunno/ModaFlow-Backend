package com.modaflow.backend.integracao.linx.write;

import com.modaflow.backend.security.TenantAccessValidator;
import com.modaflow.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static com.modaflow.backend.integracao.linx.write.LinxWriteContracts.*;

@RestController
@RequestMapping("/linx/integracao")
public class LinxWriteController {
    private final LinxWriteService service;
    private final TenantAccessValidator access;
    public LinxWriteController(LinxWriteService service,TenantAccessValidator access) { this.service=service; this.access=access; }
    @GetMapping("/capacidade") public Capability capability() {
        access.requireTenantForCurrentUser(); return service.capability();
    }
    @GetMapping("/revisao") public SchemaReview review(@AuthenticationPrincipal UserPrincipal user) {
        access.requireTenantForCurrentUser(); return service.review(userId(user));
    }
    @PostMapping("/preparar") public Preview prepare(@Valid @RequestBody Request request,@AuthenticationPrincipal UserPrincipal user) {
        long tenant = access.requireTenantForCurrentUser();
        return service.prepare(tenant,userId(user),request);
    }
    @PostMapping("/confirmar") public Receipt confirm(@Valid @RequestBody Confirm confirmation,@AuthenticationPrincipal UserPrincipal user) {
        long tenant = access.requireTenantForCurrentUser();
        return service.confirm(tenant,userId(user),confirmation);
    }
    @PostMapping("/versoes/preparar") public Preview prepareVersion(@Valid @RequestBody VersionRequest request,@AuthenticationPrincipal UserPrincipal user) {
        return service.prepareVersion(access.requireTenantForCurrentUser(),userId(user),request);
    }
    @PostMapping("/versoes/confirmar") public Receipt confirmVersion(@Valid @RequestBody Confirm confirmation,@AuthenticationPrincipal UserPrincipal user) {
        return service.confirmVersion(access.requireTenantForCurrentUser(),userId(user),confirmation);
    }
    private long userId(UserPrincipal user) {
        if (user==null || user.getId()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autentique-se antes de integrar.");
        return user.getId();
    }
}
