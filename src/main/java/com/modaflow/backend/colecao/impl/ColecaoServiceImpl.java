package com.modaflow.backend.colecao.impl;

import com.modaflow.backend.colecao.dto.CriarColecaoRequestDTO;
import com.modaflow.backend.colecao.dto.AtualizarColecaoRequestDTO;
import com.modaflow.backend.colecao.dto.ColecaoDetalheResponseDTO;
import com.modaflow.backend.colecao.dto.ColecaoResponseDTO;
import com.modaflow.backend.colecao.StatusColecaoEnum;
import com.modaflow.backend.colecao.ColecaoService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.modaflow.backend.colecao.ColecaoRepository;
import com.modaflow.backend.colecao.ColecaoMapper;
import com.modaflow.backend.colecao.Colecao;
import com.modaflow.backend.marca.MarcaRepository;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.security.TenantAccessValidator;

import java.util.List;

/**
 * SERVIÇO (IMPL): ColecaoServiceImpl
 * DESCRIÇÃO: Lógica de negócio para coleções de moda, cálculo de progresso (%) e peças concluídas.
 */
@Service
public class ColecaoServiceImpl implements ColecaoService {
    @Autowired private ColecaoRepository repository; @Autowired private ColecaoMapper mapper; @Autowired private MarcaRepository marcas; @Autowired private TenantAccessValidator tenantAccess;

    @Override
    public List<ColecaoResponseDTO> listarPorStatus(StatusColecaoEnum status) {
        Long tenant=tenantAccess.requireTenantForCurrentUser(); return (status==null?repository.findByMarcaId(tenant):repository.findByMarcaIdAndStatus(tenant,status)).stream().map(this::toResponse).toList();
    }

    @Override
    public ColecaoDetalheResponseDTO buscarDetalhesComPecas(Long id) {
        Colecao c=requireOwned(id); return mapper.toDetalheResponseDTO(c);
    }

    @Override
    public ColecaoResponseDTO criar(CriarColecaoRequestDTO request) {
        Long tenant=tenantAccess.requireTenantForCurrentUser(); Colecao c=mapper.toEntity(request);c.setMarca(marcas.getReferenceById(tenant));return toResponse(repository.save(c));
    }

    @Override
    public ColecaoResponseDTO atualizar(Long id, AtualizarColecaoRequestDTO request) {
        Colecao c=requireOwned(id);if(request.getNome()!=null)c.setNome(request.getNome());if(request.getStatus()!=null)c.setStatus(request.getStatus());if(request.getDataEntrega()!=null)c.setDataEntrega(request.getDataEntrega());return toResponse(repository.save(c));
    }
    private Colecao requireOwned(Long id){Colecao c=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Coleção não encontrada."));if(!tenantAccess.requireTenantForCurrentUser().equals(c.getMarca().getId()))throw new ResourceNotFoundException("Coleção não encontrada.");return c;}
    private ColecaoResponseDTO toResponse(Colecao c){ColecaoResponseDTO d=mapper.toResponseDTO(c);int total=c.getPecas()==null?0:c.getPecas().size();d.setPecasTotal(total);d.setPecasConcluidas((int)(c.getPecas()==null?0:c.getPecas().stream().filter(p->p.getStatus()==com.modaflow.backend.peca.StatusPecaEnum.APROVADO).count()));d.setProgressoPercent(total==0?0:d.getPecasConcluidas()*100/total);return d;}
}
