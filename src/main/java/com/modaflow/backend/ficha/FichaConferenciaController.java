package com.modaflow.backend.ficha;

import com.modaflow.backend.security.TenantAccessValidator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fichas")
public class FichaConferenciaController {
    private final FichaConferenciaService service;
    private final TenantAccessValidator tenantAccess;
    public FichaConferenciaController(FichaConferenciaService service, TenantAccessValidator tenantAccess) {
        this.service = service; this.tenantAccess = tenantAccess;
    }
    @PostMapping("/conferencia")
    public FichaConferenciaService.Resultado conferir(@Valid @RequestBody FichaConferenciaRequest ficha) {
        tenantAccess.requireTenantForCurrentUser();
        return service.conferir(ficha);
    }
}
