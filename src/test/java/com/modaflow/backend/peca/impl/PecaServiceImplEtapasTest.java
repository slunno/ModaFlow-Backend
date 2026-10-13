package com.modaflow.backend.peca.impl;

import com.modaflow.backend.colecao.Colecao;
import com.modaflow.backend.colecao.ColecaoRepository;
import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.ficha.FichaConferenciaRequest;
import com.modaflow.backend.ficha.FichaConferenciaService;
import com.modaflow.backend.marca.Marca;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.modaflow.backend.peca.Peca;
import com.modaflow.backend.peca.PecaMapper;
import com.modaflow.backend.peca.PecaRepository;
import com.modaflow.backend.peca.dto.CriarPecaRequestDTO;
import com.modaflow.backend.peca.dto.PecaResponseDTO;
import com.modaflow.backend.security.TenantAccessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Repositórios simulados: não inicia aplicação nem abre conexão SQL. */
@ExtendWith(MockitoExtension.class)
class PecaServiceImplEtapasTest {
    @Mock private PecaRepository repository;
    @Mock private PecaMapper mapper;
    @Mock private ColecaoRepository colecoes;
    @Mock private TenantAccessValidator tenantAccess;
    @Spy private FichaConferenciaService conferencia = new FichaConferenciaService();
    @InjectMocks private PecaServiceImpl service;
    private Peca peca;
    private Colecao colecao;

    @BeforeEach void prepararEntidades() {
        Marca marca = new Marca();
        marca.setId(1L);
        colecao = new Colecao();
        colecao.setId(2L);
        colecao.setNome("Coleção sintética");
        colecao.setMarca(marca);
        peca = new Peca();
        peca.setId(3L);
        peca.setMarca(marca);
        peca.setColecao(colecao);
    }

    private void prepararBusca(EtapaPecaEnum etapa) {
        peca.setEtapaAtual(etapa);
        when(repository.findById(3L)).thenReturn(Optional.of(peca));
        when(tenantAccess.requireTenantForCurrentUser()).thenReturn(1L);
    }

    private void prepararSalvamento() {
        when(repository.save(any(Peca.class))).thenAnswer(call -> call.getArgument(0));
        when(mapper.toResponseDTO(any(Peca.class))).thenAnswer(call -> {
            PecaResponseDTO resposta = new PecaResponseDTO();
            resposta.setEtapaAtual(((Peca) call.getArgument(0)).getEtapaAtual());
            return resposta;
        });
    }

    @Test void requisicaoOmitindoEtapaComecaNaGeracao() {
        assertEquals(EtapaPecaEnum.GERACAO_FICHA, new CriarPecaRequestDTO().getEtapaAtual());
    }

    @Test void criacaoSempreComecaEm01MesmoSeClienteEnviarOutraEtapa() {
        var request = new CriarPecaRequestDTO();
        request.setColecaoId(2L);
        request.setEtapaAtual(EtapaPecaEnum.FINAL);
        peca.setEtapaAtual(EtapaPecaEnum.FINAL);
        when(tenantAccess.requireTenantForCurrentUser()).thenReturn(1L);
        when(colecoes.findById(2L)).thenReturn(Optional.of(colecao));
        when(mapper.toEntity(request)).thenReturn(peca);
        prepararSalvamento();

        assertEquals(EtapaPecaEnum.GERACAO_FICHA, service.criar(request).getEtapaAtual());
        assertEquals(EtapaPecaEnum.GERACAO_FICHA, peca.getEtapaAtual());
        verify(repository).save(peca);
        verify(conferencia, never()).exigirValida(any());
    }

    @Test void moverSimplesNaoContornaConferencia01Para02() {
        prepararBusca(EtapaPecaEnum.GERACAO_FICHA);
        assertThrows(BusinessRuleException.class,
                () -> service.moverEtapa(3L, EtapaPecaEnum.ENGENHARIA_RECEBIMENTO));
        assertEquals(EtapaPecaEnum.GERACAO_FICHA, peca.getEtapaAtual());
        verify(repository, never()).save(any());
    }

    @Test void entregar01Para02RejeitaFichaIncompletaSemSalvar() {
        prepararBusca(EtapaPecaEnum.GERACAO_FICHA);
        assertThrows(BusinessRuleException.class,
                () -> service.entregarFicha(3L, EtapaPecaEnum.ENGENHARIA_RECEBIMENTO, null));
        verify(conferencia).exigirValida(null);
        assertEquals(EtapaPecaEnum.GERACAO_FICHA, peca.getEtapaAtual());
        verify(repository, never()).save(any());
    }

    @Test void entregar01Para02AceitaConferenciaAtualPorVariante() {
        prepararBusca(EtapaPecaEnum.GERACAO_FICHA);
        prepararSalvamento();
        var registro = new FichaConferenciaRequest.Registro("2026-10-08T12:00:00.000Z", "u", "Usuário sintético", "[\"principal\",[\"P\"],[]]");
        var naoSeAplica = new FichaConferenciaRequest.Decisao(false, registro);
        var variante = new FichaConferenciaRequest.Variante("principal", "Principal",
                Map.of("ziper", naoSeAplica, "etiqueta", naoSeAplica, "elastico", naoSeAplica, "entretela", naoSeAplica), List.of());
        var ficha = new FichaConferenciaRequest(List.of("P"), true, false, List.of(variante));

        assertEquals(EtapaPecaEnum.ENGENHARIA_RECEBIMENTO,
                service.entregarFicha(3L, EtapaPecaEnum.ENGENHARIA_RECEBIMENTO, ficha).getEtapaAtual());
        verify(conferencia).exigirValida(ficha);
        verify(repository).save(peca);
    }

    @Test void mover02Para03NaoExigeConferencia() {
        prepararBusca(EtapaPecaEnum.ENGENHARIA_RECEBIMENTO);
        prepararSalvamento();
        assertEquals(EtapaPecaEnum.MODELAGEM, service.moverEtapa(3L, EtapaPecaEnum.MODELAGEM).getEtapaAtual());
        verify(conferencia, never()).exigirValida(any());
        verify(repository).save(peca);
    }

    @Test void entregar02Para03NaoExigeConferencia() {
        prepararBusca(EtapaPecaEnum.ENGENHARIA_RECEBIMENTO);
        prepararSalvamento();
        assertEquals(EtapaPecaEnum.MODELAGEM,
                service.entregarFicha(3L, EtapaPecaEnum.MODELAGEM, null).getEtapaAtual());
        verify(conferencia, never()).exigirValida(any());
        verify(repository).save(peca);
    }

    @Test void permissaoDeMarcaContinuaObrigatoriaMesmoSemConferencia() {
        prepararBusca(EtapaPecaEnum.ENGENHARIA_RECEBIMENTO);
        peca.getMarca().setId(9L);
        assertThrows(ResourceNotFoundException.class,
                () -> service.moverEtapa(3L, EtapaPecaEnum.MODELAGEM));
        verify(repository, never()).save(any());
    }
}
