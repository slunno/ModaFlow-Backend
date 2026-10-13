package com.modaflow.backend.marca.impl;

import com.modaflow.backend.common.exception.ResourceNotFoundException;

import com.modaflow.backend.marca.dto.CriarMarcaRequestDTO;
import com.modaflow.backend.marca.dto.AtualizarMarcaRequestDTO;
import com.modaflow.backend.marca.dto.MarcaResponseDTO;
import com.modaflow.backend.marca.MarcaService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.modaflow.backend.marca.MarcaRepository;
import com.modaflow.backend.marca.MarcaMapper;
import com.modaflow.backend.marca.Marca;

import java.util.List;

/**
 * SERVIÇO (IMPL): MarcaServiceImpl
 * DESCRIÇÃO: Implementação das regras de negócio de criação e edição de marcas da AKR BRANDS.
 */
@Service
public class MarcaServiceImpl implements MarcaService {
    @Autowired private MarcaRepository repository;
    @Autowired private MarcaMapper mapper;

    @Override
    public List<MarcaResponseDTO> listarTodas() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public MarcaResponseDTO buscarPorId(Long id) {
        return toResponse(require(id));
    }

    @Override
    public MarcaResponseDTO criar(CriarMarcaRequestDTO request) {
        return toResponse(repository.save(mapper.toEntity(request)));
    }

    @Override
    public MarcaResponseDTO atualizar(Long id, AtualizarMarcaRequestDTO request) {
        Marca marca=require(id); if(request.getNome()!=null)marca.setNome(request.getNome()); if(request.getCode()!=null)marca.setCode(request.getCode()); if(request.getInitials()!=null)marca.setInitials(request.getInitials()); if(request.getHeroImageUrl()!=null)marca.setHeroImageUrl(request.getHeroImageUrl()); if(request.getLogoUrl()!=null)marca.setLogoUrl(request.getLogoUrl()); if(request.getDescription()!=null)marca.setDescription(request.getDescription()); if(request.getBadgeTag()!=null)marca.setBadgeTag(request.getBadgeTag()); return toResponse(repository.save(marca));
    }
    private Marca require(Long id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Marca não encontrada."));}
    private MarcaResponseDTO toResponse(Marca marca){MarcaResponseDTO dto=mapper.toResponseDTO(marca);dto.setColecoesCount(marca.getColecoes()==null?0:marca.getColecoes().size());dto.setPecasCount(marca.getColecoes()==null?0:marca.getColecoes().stream().mapToInt(c->c.getPecas()==null?0:c.getPecas().size()).sum());return dto;}
}
