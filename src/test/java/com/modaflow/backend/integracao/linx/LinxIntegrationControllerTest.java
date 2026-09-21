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
