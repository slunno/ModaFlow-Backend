package com.modaflow.backend.integracao.linx;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LinxFichaControllerTest {
    @Test void consultaV2UsaServicoCompletoEIdentidadeDaColecao() {
        var service = mock(LinxFichaCustosService.class);
        var controller = new LinxFichaController(Optional.empty(), Optional.of(service));
        controller.getCustosCompletos("TESTE", "00001", "KING&JOE", "025", "Coleção teste", true);
        verify(service).getCustosCompletos("TESTE", "00001", "KING&JOE", "025", "Coleção teste", true);
        verify(service, never()).getCustos(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test void custosDesativadosRetornamServiceUnavailable() {
        var controller = new LinxFichaController(Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> controller.getCustos("TESTE", "00001", "KING&JOE", "025", "Coleção teste", false))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test void encaminhaIdentidadeCompletaEFlagLegadoAoServico() {
        var service = mock(LinxFichaCustosService.class);
        var dto = new LinxFichaCustosDTO(null, "HOMOLOG_COBRANCA", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of());
        when(service.getCustos("TESTE", "00001", "KING&JOE", "025", "Coleção teste", true)).thenReturn(dto);
        var controller = new LinxFichaController(Optional.empty(), Optional.of(service));
        assertThat(controller.getCustos("TESTE", "00001", "KING&JOE", "025", "Coleção teste", true)).isSameAs(dto);
        verify(service).getCustos("TESTE", "00001", "KING&JOE", "025", "Coleção teste", true);
    }
}
