package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import com.modaflow.backend.peca.PecaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exclusivamente mocks; não inicia aplicação nem abre conexão SQL. */
class LinxMapeamentoLeituraTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private LinxIntegrationService integration;
    private LinxFichaService fichas;
    @BeforeEach void setup() {
        var client = mock(LinxDatabaseClient.class);
        when(client.jdbc()).thenReturn(jdbc);
        integration = spy(new LinxIntegrationService(client, mock(PecaRepository.class)));
        fichas = new LinxFichaService(client, integration);
        doReturn(new LinxConnectionInfoDTO("srv-sql-dev", "HOMOLOG_COBRANCA", "HOMOLOG_COBRANCA", true, true, true, List.of()))
            .when(integration).getConnectionInfo();
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"}) void mantemPosicoesVaziasENaoUsaNumeroTamanhosParaGerarGrade() throws Exception {
        var row = mock(ResultSet.class);
        when(row.getString("GRADE")).thenReturn("38-50  ");
        when(row.getInt("NUMERO_TAMANHOS")).thenReturn(12);
        when(row.getString("TAMANHO_1")).thenReturn("38");
        when(row.getString("TAMANHO_2")).thenReturn(" ");
        when(row.getString("TAMANHO_3")).thenReturn("42");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(row, 0)));
        var grade = integration.getGradeDetalhe(" 38-50 ");
        assertThat(grade.grade()).isEqualTo("38-50");
        assertThat(grade.numeroTamanhos()).isEqualTo(12);
        assertThat(grade.posicoes()).hasSize(48);
        assertThat(grade.posicoes().get(1)).isEqualTo(new LinxGradeDetalheDTO.Posicao(2, null));
        assertThat(grade.posicoes().get(2)).isEqualTo(new LinxGradeDetalheDTO.Posicao(3, "42"));
        assertThat(grade.posicoes().getLast().posicao()).isEqualTo(48);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("TAMANHO_48", "WHERE GRADE = :grade").doesNotContain("38-50", "UPDATE", "INSERT", "DELETE");
        assertThat(params.getValue().getValue("grade")).isEqualTo("38-50");
    }
    @Test @SuppressWarnings("unchecked") void naoInventarGradeQuandoNaoEncontrada() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> integration.getGradeDetalhe("DESCONHECIDA")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("não encontrada");
    }
    @Test @SuppressWarnings("unchecked") void rejeitaGradeAmbigua() {
        var grade = new LinxGradeDetalheDTO("P", 1, List.of());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(grade, grade));
        assertThatThrownBy(() -> integration.getGradeDetalhe("P")).isInstanceOf(BusinessRuleException.class);
    }
    @Test void invalidaParametrosAntesDeConsultar() {
        assertThatThrownBy(() -> integration.getGradeDetalhe(" ")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> integration.getGradeDetalhe("x".repeat(26))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> integration.getCoresComCodigo("x".repeat(101))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> fichas.getCoresProduto("PRODUTO", "Outra marca", "025", "Teste")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> fichas.getCoresProduto("PRODUTO", "KING&JOE", "", "Teste")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(jdbc);
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"}) void buscaCorPorCodigoOuNomeSemAgruparDescricao() throws Exception {
        var row = mock(ResultSet.class);
        when(row.getString("COR")).thenReturn(" 001 ");
        when(row.getString("DESC_COR")).thenReturn(" BRANCO ");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(row, 0)));
        assertThat(integration.getCoresComCodigo("001'--")).containsExactly(new LinxCorCodigoDTO("001", "BRANCO"));
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("TOP (50)", "CHARINDEX(:search, COR)", "CHARINDEX(:search, DESC_COR)", "USO_PRODUTOS = 1")
            .doesNotContain("DISTINCT", "001'--", "UPDATE", "INSERT", "DELETE");
        assertThat(params.getValue().getValue("search")).isEqualTo("001'--");
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"}) void separaCodigoDoProdutoECorBasicaEFiltraColecao() throws Exception {
        var row = mock(ResultSet.class);
        when(row.getString("PRODUTO")).thenReturn("BM00002 ");
        when(row.getString("COR_PRODUTO")).thenReturn("V001 ");
        when(row.getString("DESC_COR_PRODUTO")).thenReturn("Cor variante");
        when(row.getString("COR")).thenReturn("B002");
        when(row.getString("DESC_COR")).thenReturn("Cor básica");
        when(row.getString("U_COLECAO")).thenReturn("025");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(row, 0)));
        assertThat(fichas.getCoresProduto("BM00002", "KING&JOE", "025", " Teste "))
            .containsExactly(new LinxCorProdutoDTO("BM00002", "V001", "Cor variante", "B002", "Cor básica", "025", false));
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("CB.COR = PC.COR", "P.COLECAO = :colecao", "P.GRIFFE = :griffe", "C.GRIFFE = :griffe", "C.DESC_COLECAO", "PC.U_COLECAO = :colecao")
            .doesNotContain("PC.COR_PRODUTO = CB.COR", "BM00002", "UPDATE", "INSERT", "DELETE");
        assertThat(params.getValue().getValue("descColecao")).isEqualTo("Teste");
    }
    @Test void guardaDeBancoBloqueiaNovasConsultas() {
        doReturn(new LinxConnectionInfoDTO("outro", "OUTRO", "HOMOLOG_COBRANCA", false, true, true, List.of())).when(integration).getConnectionInfo();
        assertThatThrownBy(() -> integration.getGradeDetalhe("P")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> integration.getCoresComCodigo("")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> fichas.getCoresProduto("P1", "KING&JOE", "025", "Teste")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void novosEndpointsNaoAtivamIntegracaoDesligada() {
        var controller = new LinxIntegrationController(Optional.empty());
        var fichaController = new LinxFichaController(Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> controller.getGradeDetalhe("P")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> controller.getCoresComCodigo("")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> fichaController.getCoresProduto("025", "P1", "KING&JOE", "Teste")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
