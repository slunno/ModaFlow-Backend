package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.integracao.linx.LinxOpcoesResponseDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * ============================================================================
 * SERVIÇO: LinxIntegrationService
 * DESCRIÇÃO: Consulta direta às tabelas nativas do Linx no SQL Server
 *            (HOMOLOGACAO_COBRANCA) para alimentar os seletores do PLM.
 * ============================================================================
 */
@Service
public class LinxIntegrationService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Sub-Grupo:
     * SELECT PRODUTOS_SUBGRUPO.SUBGRUPO_PRODUTO FROM PRODUTOS_SUBGRUPO WHERE GRUPO_PRODUTO = 'ROUPAS'
     */
    @Transactional(readOnly = true)
    public List<String> getSubgrupos() {
        try {
            String sql = "SELECT DISTINCT RTRIM(LTRIM(SUBGRUPO_PRODUTO)) " +
                         "FROM PRODUTOS_SUBGRUPO " +
                         "WHERE GRUPO_PRODUTO = 'ROUPAS' AND SUBGRUPO_PRODUTO IS NOT NULL " +
                         "ORDER BY 1";
            return executeListQuery(sql);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Linha:
     * SELECT PRODUTOS_LINHAS.LINHA FROM PRODUTOS_LINHAS ORDER BY LINHA
     */
    @Transactional(readOnly = true)
    public List<String> getLinhas() {
        try {
            String sql = "SELECT DISTINCT RTRIM(LTRIM(LINHA)) " +
                         "FROM PRODUTOS_LINHAS " +
                         "WHERE LINHA IS NOT NULL AND RTRIM(LTRIM(LINHA)) <> '' " +
                         "ORDER BY 1";
            return executeListQuery(sql);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Griffe:
     * SELECT DISTINCT GRIFFE FROM dbo.COLECOES WHERE INATIVO = 0
     */
    @Transactional(readOnly = true)
    public List<String> getGriffes() {
        try {
            String sql = "SELECT DISTINCT RTRIM(LTRIM(GRIFFE)) " +
                         "FROM dbo.COLECOES " +
                         "WHERE INATIVO = 0 AND GRIFFE IS NOT NULL AND RTRIM(LTRIM(GRIFFE)) <> '' " +
                         "ORDER BY 1";
            return executeListQuery(sql);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Grades:
     * SELECT PRODUTOS_TAMANHOS.GRADE FROM DBO.PRODUTOS_TAMANHOS
     */
    @Transactional(readOnly = true)
    public List<String> getGrades() {
        try {
            String sql = "SELECT DISTINCT RTRIM(LTRIM(GRADE)) " +
                         "FROM DBO.PRODUTOS_TAMANHOS " +
                         "WHERE GRADE IS NOT NULL AND RTRIM(LTRIM(GRADE)) <> '' " +
                         "ORDER BY 1";
            return executeListQuery(sql);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Categoria:
     * SELECT PRODUTOS_CATEGORIA.CATEGORIA_PRODUTO FROM PRODUTOS_CATEGORIA
     */
    @Transactional(readOnly = true)
    public List<String> getCategorias() {
        try {
            String sql = "SELECT DISTINCT RTRIM(LTRIM(CATEGORIA_PRODUTO)) " +
                         "FROM PRODUTOS_CATEGORIA " +
                         "WHERE CATEGORIA_PRODUTO IS NOT NULL AND RTRIM(LTRIM(CATEGORIA_PRODUTO)) <> '' " +
                         "ORDER BY 1";
            return executeListQuery(sql);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Retorna o consolidado com todas as opções do Linx de uma só vez para o frontend.
     */
    @Transactional(readOnly = true)
    public LinxOpcoesResponseDTO getOpcoesLinx() {
        return new LinxOpcoesResponseDTO(
            getSubgrupos(),
            getLinhas(),
            getGriffes(),
            getGrades(),
            getCategorias()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> executeListQuery(String sql) {
        return entityManager.createNativeQuery(sql).getResultList();
    }
}
