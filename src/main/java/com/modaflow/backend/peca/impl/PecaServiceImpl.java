package com.modaflow.backend.peca.impl;

import com.modaflow.backend.peca.dto.CriarPecaRequestDTO;
import com.modaflow.backend.peca.dto.AtualizarPecaRequestDTO;
import com.modaflow.backend.peca.dto.PecaResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.modaflow.backend.peca.PecaService;
import com.modaflow.backend.peca.PecaRepository;
import com.modaflow.backend.peca.PecaMapper;
import com.modaflow.backend.peca.Peca;
import com.modaflow.backend.colecao.ColecaoRepository;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.security.TenantAccessValidator;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * SERVIÇO (IMPL): PecaServiceImpl
 * DESCRIÇÃO: Lógica de peças de roupa, cálculo de atraso em dias e avanço de etapa no PLM.
 */
@Service
public class PecaServiceImpl implements PecaService {
    @Autowired private PecaRepository repository;
    @Autowired private PecaMapper mapper;
    @Autowired private ColecaoRepository colecoes;
    @Autowired private TenantAccessValidator tenantAccess;
    @Autowired private com.modaflow.backend.ficha.FichaConferenciaService conferencia;

    @Override
    public Page<PecaResponseDTO> listarComFiltros(Pageable pageable) {
        return repository.findByMarcaId(tenantAccess.requireTenantForCurrentUser(), pageable).map(this::toResponse);
    }

    @Override
    public PecaResponseDTO buscarPorId(Long id) {
        return toResponse(requireOwned(id));
    }

    @Override
    public PecaResponseDTO criar(CriarPecaRequestDTO request) {
        Long tenantId = tenantAccess.requireTenantForCurrentUser();
        var colecao = colecoes.findById(request.getColecaoId()).orElseThrow(() -> new ResourceNotFoundException("Coleção não encontrada."));
        if (!tenantId.equals(colecao.getMarca().getId())) throw new ResourceNotFoundException("Coleção não encontrada.");
        Peca peca = mapper.toEntity(request);
        peca.setEtapaAtual(EtapaPecaEnum.GERACAO_FICHA);
        peca.setColecao(colecao); peca.setMarca(colecao.getMarca());
        return toResponse(repository.save(peca));
    }

    @Override
    public PecaResponseDTO atualizar(Long id, AtualizarPecaRequestDTO request) {
        Peca p = requireOwned(id);
        if(request.getNome()!=null)p.setNome(request.getNome()); if(request.getTipo()!=null)p.setTipo(request.getTipo());
        if(request.getStatus()!=null)p.setStatus(request.getStatus()); if(request.getTema()!=null)p.setTema(request.getTema());
        if(request.getEstilista()!=null)p.setEstilista(request.getEstilista()); if(request.getModelista()!=null)p.setModelista(request.getModelista());
        if(request.getImagemCroquiUrl()!=null)p.setImagemCroquiUrl(request.getImagemCroquiUrl()); if(request.getCusto()!=null)p.setCusto(request.getCusto());
        if(request.getPreco()!=null)p.setPreco(request.getPreco()); if(request.getPrevisaoEntrega()!=null)p.setPrevisaoEntrega(request.getPrevisaoEntrega());
        return toResponse(repository.save(p));
    }

    @Override
    public PecaResponseDTO moverEtapa(Long id, EtapaPecaEnum novaEtapa) {
        Peca p = requireOwned(id);
        conferencia.exigirConferenciaNaMudanca(p.getEtapaAtual(), novaEtapa);
        p.setEtapaAtual(novaEtapa); return toResponse(repository.save(p));
    }

    @Override
    public PecaResponseDTO entregarFicha(Long id, EtapaPecaEnum novaEtapa, com.modaflow.backend.ficha.FichaConferenciaRequest ficha) {
        Peca p = requireOwned(id);
        if (conferencia.exigeConferenciaNaTransicao(p.getEtapaAtual(), novaEtapa)) conferencia.exigirValida(ficha);
        p.setEtapaAtual(novaEtapa);
        return toResponse(repository.save(p));
    }

    private Peca requireOwned(Long id) {
        Peca p = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Peça não encontrada."));
        if (!tenantAccess.requireTenantForCurrentUser().equals(p.getMarca().getId())) throw new ResourceNotFoundException("Peça não encontrada.");
        return p;
    }
    private PecaResponseDTO toResponse(Peca p) {
        PecaResponseDTO dto = mapper.toResponseDTO(p); dto.setColecaoId(p.getColecao().getId()); dto.setColecaoNome(p.getColecao().getNome());
        dto.setDiasAtraso(p.getPrevisaoEntrega() != null && p.getPrevisaoEntrega().isBefore(LocalDate.now()) ? (int) java.time.temporal.ChronoUnit.DAYS.between(p.getPrevisaoEntrega(), LocalDate.now()) : 0); return dto;
    }
}
