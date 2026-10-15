package com.modaflow.backend.integracao.linx.write;

import com.modaflow.backend.common.exception.GlobalExceptionHandler;
import com.modaflow.backend.common.exception.TenantAccessDeniedException;
import com.modaflow.backend.security.TenantAccessValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.modaflow.backend.integracao.linx.write.LinxWriteContracts.*;

class LinxWriteControllerTest {
    @Test void capacidadeDesligadaNaoRequerPoolOuBanco() throws Exception {
        var service=mock(LinxWriteService.class); var access=mock(TenantAccessValidator.class);
        when(access.requireTenantForCurrentUser()).thenReturn(1L);
        when(service.capability()).thenReturn(new Capability(false,"192.168.1.6:1433","HOMOLOG_COBRANCA","CAMPOS_MAPEADOS","Desativado"));
        var mvc=MockMvcBuilders.standaloneSetup(new LinxWriteController(service,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/linx/integracao/capacidade")).andExpect(status().isOk())
            .andExpect(jsonPath("$.habilitada").value(false)).andExpect(jsonPath("$.banco").value("HOMOLOG_COBRANCA"));
        verify(access).requireTenantForCurrentUser();
    }
    @Test void marcaSemAcessoNaoPodeNemInspecionarCapacidade() throws Exception {
        var service=mock(LinxWriteService.class); var access=mock(TenantAccessValidator.class);
        when(access.requireTenantForCurrentUser()).thenThrow(new TenantAccessDeniedException("Sem acesso à marca"));
        var mvc=MockMvcBuilders.standaloneSetup(new LinxWriteController(service,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/linx/integracao/capacidade")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void naoAceitaConfirmacaoSemTokenValido() throws Exception {
        var service=mock(LinxWriteService.class); var access=mock(TenantAccessValidator.class);
        var mvc=MockMvcBuilders.standaloneSetup(new LinxWriteController(service,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/linx/integracao/confirmar").contentType("application/json").content("{\"confirmar\":true}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }
}
