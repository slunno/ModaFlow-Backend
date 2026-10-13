package com.modaflow.backend.kanban.impl;

import com.modaflow.backend.peca.dto.PecaKanbanResponseDTO;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.modaflow.backend.kanban.KanbanService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.modaflow.backend.peca.PecaRepository;
import com.modaflow.backend.peca.PecaMapper;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.security.TenantAccessValidator;
import java.time.LocalDate;

import java.util.List;

/**
 * SERVIÇO (IMPL): KanbanServiceImpl
 * DESCRIÇÃO: Lógica para agrupamento de peças por etapa e atualização de colunas do Kanban.
 */
@Service
public class KanbanServiceImpl implements KanbanService {
    @Autowired private PecaRepository pecas;
    @Autowired private PecaMapper mapper;
    @Autowired private TenantAccessValidator tenantAccess;
    @Autowired private com.modaflow.backend.ficha.FichaConferenciaService conferencia;

    @Override
    public List<PecaKanbanResponseDTO> obterPecasDoKanban() {
        return pecas.findByMarcaId(tenantAccess.requireTenantForCurrentUser(), org.springframework.data.domain.Pageable.unpaged()).map(p -> { var dto=mapper.toKanbanResponseDTO(p); dto.setDiasAtraso(p.getPrevisaoEntrega()!=null && p.getPrevisaoEntrega().isBefore(LocalDate.now()) ? (int) java.time.temporal.ChronoUnit.DAYS.between(p.getPrevisaoEntrega(),LocalDate.now()) : 0); return dto; }).getContent();
    }

    @Override
    public void moverPecaNoKanban(Long pecaId, EtapaPecaEnum novaEtapa) {
        var p=pecas.findById(pecaId).orElseThrow(() -> new ResourceNotFoundException("Peça não encontrada."));
        if(!tenantAccess.requireTenantForCurrentUser().equals(p.getMarca().getId())) throw new ResourceNotFoundException("Peça não encontrada.");
        conferencia.exigirConferenciaNaMudanca(p.getEtapaAtual(), novaEtapa);
        p.setEtapaAtual(novaEtapa); pecas.save(p);
    }
}
