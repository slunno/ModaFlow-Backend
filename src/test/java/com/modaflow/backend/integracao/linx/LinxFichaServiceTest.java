package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LinxFichaServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final LinxIntegrationService integration = mock(LinxIntegrationService.class);
    private LinxFichaService service;
    @BeforeEach void setup() {
        var client = mock(LinxDatabaseClient.class);
        when(client.jdbc()).thenReturn(jdbc);
        service = new LinxFichaService(client, integration);
    }
    @Test @SuppressWarnings("unchecked") void limitaVersoesSemInferirColecaoPorDataOuSufixo() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        service.getVersoes(" BM24215 ", "K&J BLACK", "025", "Coleção teste");
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("P.GRIFFE = :griffe", "H.DATA_CRIACAO DESC", "H.U_COLECAO = :colecao", "C.DESC_COLECAO")
            .doesNotContain("RIGHT(", "DATA_INICIO_META", "BM24215");
        assertThat(params.getValue().getValue("produto")).isEqualTo("BM24215");
        verify(integration).requireExpectedDatabase();
    }
    @Test @SuppressWarnings("unchecked") void preservaValoresOriginaisEConsultaSomenteProdutoEVersao() {
        var header = new LinxFichaVersaoDTO("TESTE", "00002", null, null, null, null, "025", null);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(header));
        Map<String, Object> material = Map.of("MATERIAL", " 01001 ", "ITEM", "0001", "DESC_MATERIAL", " Nome real ",
            "GRUPO", "TECIDO", "C1", BigDecimal.ZERO, "FABRICANTE", "Fabricante real");
        Map<String, Object> color = Map.of("MATERIAL", "01001", "ITEM", "0001", "COR_MATERIAL", "001", "GRAMATURA", BigDecimal.ZERO);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(material), List.of(color));
        var result = service.getDetalhe("TESTE", "00002", "KING&JOE", "025", "Coleção teste");
        assertThat(result.versao()).isEqualTo(header);
        assertThat(result.materiais().getFirst().nome()).isEqualTo("Nome real");
        assertThat(result.materiais().getFirst().dados()).containsEntry("C1", BigDecimal.ZERO)
            .containsEntry("FABRICANTE", "Fabricante real");
        assertThat(result.materiais().getFirst().cores()).hasSize(1);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getAllValues().getFirst()).contains("V.C48 FROM", "V.PRODUTO = :produto", "V.VERSAO_FICHA = :versao").doesNotContain("M.GRUPO IN")
            .doesNotContain("TIMESTAMP", "UPDATE", "INSERT", "DELETE");
    }
    @Test void montaFiltrosApenasComVinculoExplicitoEDeduplicaMateriais() {
        var row = Map.<String, Object>of("PRODUTO", "TESTE", "VERSAO_FICHA", "00001", "GRUPO", "TECIDO", "MATERIAL", "01001", "DESC_MATERIAL", "Material");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row, row));
        var result = service.getResumosDaColecao("025", "KING&JOE", "Coleção teste", false);
        assertThat(result.getFirst().tecidos()).containsExactly("Material (01001)");
        assertThat(result.getFirst().aviamentos()).isEmpty();
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue()).contains("V.U_COLECAO = C.COLECAO", "P.GRIFFE = :griffe", "C.COLECAO = :colecao")
            .doesNotContain("DATA_INICIO_META", "DATA_FINAL_META");
    }
    @Test @SuppressWarnings("unchecked") void naoRetornaMateriaisParaUmaVersaoInexistenteOuOutraGriffe() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.getDetalhe("TESTE", "00003", "KING&JOE", "025", "Coleção teste"))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class));
    }
    @Test void validaGriffeEParametrosAntesDeLerDados() {
        assertThatThrownBy(() -> service.getVersoes("TESTE", "Outra marca", "025", "Coleção teste")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.getDetalhe("TESTE", "000001", "KING&JOE", "025", "Coleção teste")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.getVersoes("TESTE", "KING&JOE", "", "Coleção teste")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(jdbc);
    }
    @Test @SuppressWarnings("unchecked") void baseAntigaConsultaSomenteUmaFichaAtualDaPecaDaColecao() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.getAtual("TESTE", "KING&JOE", "025", "Verão teste")).isInstanceOf(ResourceNotFoundException.class);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("TOP (1)", "P.COLECAO = :colecao", "PC.DESC_COLECAO", "H.U_COLECAO = :colecao")
            .doesNotContain("TOP (3)", "UPDATE", "INSERT");
        assertThat(params.getValue().getValue("descColecao")).isEqualTo("Verão teste");
        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class));
    }
    @Test void resumoLegadoNaoAdmiteFichaExplicitamenteDeOutraColecao() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        service.getResumosDaColecao("025", "KING&JOE", "Verão teste", true);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(sql.capture(), params.capture());
        assertThat(sql.getValue()).contains("V.U_COLECAO = C.COLECAO", "NULLIF(LTRIM(RTRIM(V.U_COLECAO)), '') IS NULL", "C.DESC_COLECAO");
        assertThat(params.getValue().getValue("legado")).isEqualTo(1);
    }
}
