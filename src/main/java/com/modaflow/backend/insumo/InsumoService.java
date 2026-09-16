package com.modaflow.backend.insumo;

import com.modaflow.backend.insumo.dto.CriarInsumoRequestDTO;
import com.modaflow.backend.insumo.dto.AtualizarInsumoRequestDTO;
import com.modaflow.backend.insumo.dto.InsumoResponseDTO;
import com.modaflow.backend.insumo.TipoInsumoEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * SERVIÇO: InsumoService
 * DESCRIÇÃO: Operações de Tecidos e Aviamentos (listar por tipo, CRUD, importação em lote CSV/Excel).
 */
public interface InsumoService {
    Page<InsumoResponseDTO> listarPorTipo(TipoInsumoEnum tipo, Pageable pageable);
    InsumoResponseDTO buscarPorId(Long id);
    InsumoResponseDTO criar(CriarInsumoRequestDTO request);
    InsumoResponseDTO atualizar(Long id, AtualizarInsumoRequestDTO request);
    void excluir(Long id);
    int importarLote(MultipartFile file, TipoInsumoEnum tipo);
}
