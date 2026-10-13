package com.modaflow.backend.ficha;

import com.modaflow.backend.security.TenantAccessValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FichaConferenciaControllerTest {
    @Test void desserializaContratoFrontendEVerificaMarcaSemBanco() throws Exception {
        var tenant = mock(TenantAccessValidator.class);
        when(tenant.requireTenantForCurrentUser()).thenReturn(1L);
        var mvc = MockMvcBuilders.standaloneSetup(new FichaConferenciaController(new FichaConferenciaService(), tenant)).build();
        var item = new FichaConferenciaRequest.Insumo("a", "Material teste", "A1", "aviamentos", null, "0,3671", "Metros", "1,2", null);
        var ficha = FichaConferenciaServiceTest.confirmarAusentes(new FichaConferenciaRequest(List.of("P", "M"), true, true, List.of(new FichaConferenciaRequest.Variante("v", "Principal", Map.of(), List.of(item)))));
        mvc.perform(post("/fichas/conferencia").contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(ficha)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valida").value(true)).andExpect(jsonPath("$.pendencias").isEmpty());
        verify(tenant).requireTenantForCurrentUser();
    }
    @Test void fichaIncompletaRetornaPendenciasSemSalvarNada() throws Exception {
        var tenant = mock(TenantAccessValidator.class);
        when(tenant.requireTenantForCurrentUser()).thenReturn(1L);
        var mvc = MockMvcBuilders.standaloneSetup(new FichaConferenciaController(new FichaConferenciaService(), tenant)).build();
        mvc.perform(post("/fichas/conferencia").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tamanhos\":[],\"gradeCompleta\":false,\"variantes\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valida").value(false)).andExpect(jsonPath("$.pendencias").isNotEmpty());
    }
}
