package com.modaflow.backend.integracao.linx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class LinxIntegrationControllerTest {

    @Test
    void expoeMostruarioComCodigoSomentePorGet() throws Exception {
        var service = mock(LinxIntegrationService.class);
        when(service.getCoresMostruarioComCodigo()).thenReturn(List.of(new LinxCorCodigoDTO("00003", "PRETO")));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new LinxIntegrationController(Optional.of(service))).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/linx/cores-mostruario/codigos"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].codigo").value("00003"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].descricao").value("PRETO"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/linx/cores-mostruario/codigos"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed());
        verify(service).getCoresMostruarioComCodigo();
    }

    @Test
    void informaServiceUnavailableQuandoIntegracaoNaoFoiConfigurada() {
        LinxIntegrationController controller = new LinxIntegrationController(Optional.empty());

        assertThatThrownBy(controller::getConnectionInfo)
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(error.getReason()).contains("MODAFLOW_LINX_ENABLED");
            });
    }

    @Test
    void limitaPaginacaoAntesDeConsultarOLinx() {
        LinxIntegrationService service = mock(LinxIntegrationService.class);
        LinxProdutosValidosPageResponseDTO expected = new LinxProdutosValidosPageResponseDTO(
            List.of(), 0, 200, 0, 0, false, "HOMOLOGACAO_COBRANCA", true
        );
        when(service.getProdutosValidos(
            0, 200, null, null, null, null, null, null, null, null
        )).thenReturn(expected);

        LinxIntegrationController controller = new LinxIntegrationController(Optional.of(service));
        var response = controller.getProdutosValidos(
            -10, 9999, null, null, null, null, null, null, null, null
        );

        assertThat(response.getBody()).isSameAs(expected);
        verify(service).getProdutosValidos(
            0, 200, null, null, null, null, null, null, null, null
        );
    }
}
