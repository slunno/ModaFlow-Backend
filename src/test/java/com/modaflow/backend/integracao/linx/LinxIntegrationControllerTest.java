package com.modaflow.backend.integracao.linx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinxIntegrationControllerTest {

    @Test
    void limitaPaginacaoAntesDeConsultarOLinx() {
        LinxIntegrationService service = mock(LinxIntegrationService.class);
        LinxProdutosValidosPageResponseDTO expected = new LinxProdutosValidosPageResponseDTO(
            List.of(), 0, 200, 0, 0, false, "HOMOLOGACAO_COBRANCA", true
        );
        when(service.getProdutosValidos(0, 200, null, null, null, null, null)).thenReturn(expected);

        LinxIntegrationController controller = new LinxIntegrationController(service);
        var response = controller.getProdutosValidos(-10, 9999, null, null, null, null, null);

        assertThat(response.getBody()).isSameAs(expected);
        verify(service).getProdutosValidos(0, 200, null, null, null, null, null);
    }
}
