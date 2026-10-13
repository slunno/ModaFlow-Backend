package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Testes puros com JDBC mockado: não iniciam Spring nem abrem conexão SQL. */
class LinxFichaCustosServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final LinxIntegrationService integration = mock(LinxIntegrationService.class);
    private final LinxFichaService fichas = mock(LinxFichaService.class);
    private LinxFichaCustosService service;
    private final LinxFichaVersaoDTO header = new LinxFichaVersaoDTO("TESTE", "00002", null, null,
        null, null, "025", "Coleção teste");

    @BeforeEach void setup() {
        var client = mock(LinxDatabaseClient.class);
        when(client.jdbc()).thenReturn(jdbc);
        when(integration.requireExpectedDatabase()).thenReturn(new LinxConnectionInfoDTO("servidor teste",
            "HOMOLOG_COBRANCA", "HOMOLOG_COBRANCA", true, true, true, List.of()));
        when(fichas.getDetalhe("TESTE", "00002", "KING&JOE", "025", "Coleção teste"))
            .thenReturn(new LinxFichaDetalheDTO(header, List.of()));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
            .thenReturn(Map.of("OBJETO_VALIDO", 1, "DEPENDENCIAS_INSEGURAS", 0));
        service = new LinxFichaCustosService(client, integration, fichas);
    }

    private LinxFichaCustosDTO read() {
        return service.getCustos("TESTE", "00002", "KING&JOE", "025", "Coleção teste", false);
    }

    @Test void consultaCompletaNaoOmiteGruposOuTamanhosNemCruzaMateriaisDoMesmoItem() {
        var row = Map.<String, Object>of("MATERIAL", "EMB", "GRUPO", "EMBALAGEM", "POSICAO", 48,
            "CUSTO_CONSUMO_LINX", new BigDecimal("0.03000000"), "STATUS_CALCULO", "CALCULADO");
        when(jdbc.queryForList(eq(LinxFichaCustosService.MATERIAIS_COMPLETOS_SQL), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(row));
        var result = service.getCustosCompletos("TESTE", "00002", "KING&JOE", "025", "Coleção teste", false);
        assertThat(result.versaoIntegracao()).isEqualTo("custos-v2");
        assertThat(result.materiais().getFirst()).containsEntry("GRUPO", "EMBALAGEM").containsEntry("POSICAO", 48);
        assertThat(LinxFichaCustosService.MATERIAIS_COMPLETOS_SQL)
            .contains("V.C48", "T.TAMANHO_48", "VC.ITEM = V.ITEM AND VC.MATERIAL = V.MATERIAL")
            .doesNotContain("M.GRUPO IN", "WHEN J.ITEM_REUTILIZADO = 1", "UPDATE", "INSERT", "DELETE", "COALESCE", "ROUND(");
        assertThat(result.avisos()).anyMatch(text -> text.contains("cadastros atuais"));
    }

    @Test void preservaBigDecimalSemEscolherPrecoGeralOuFornecedor() {
        var precise = new BigDecimal("1.23456789012345");
        Map<String, Object> row = Map.of("PRODUTO", " TESTE ", "MATERIAL", " 01001 ",
            "CUSTO_CONSUMO_LINX", precise, "PRECO_COR_A_VISTA", BigDecimal.ZERO,
            "PRECO_MATERIAL_A_VISTA", new BigDecimal("999.00"), "STATUS_CALCULO", "CALCULADO");
        when(jdbc.queryForList(eq(LinxFichaCustosService.MATERIAIS_SQL), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(row));
        var result = read();
        assertThat(result.bancoOrigem()).isEqualTo("HOMOLOG_COBRANCA");
        assertThat(result.materiais().getFirst()).containsEntry("PRODUTO", "TESTE")
            .containsEntry("CUSTO_CONSUMO_LINX", precise).containsEntry("PRECO_COR_A_VISTA", BigDecimal.ZERO)
            .containsEntry("PRECO_MATERIAL_A_VISTA", new BigDecimal("999.00"));
        assertThat(result.avisos()).anyMatch(text -> text.contains("não snapshots"))
            .anyMatch(text -> text.contains("não o preço geral"));
        verify(integration).requireExpectedDatabase();
        verify(fichas).getDetalhe("TESTE", "00002", "KING&JOE", "025", "Coleção teste");
    }

    @Test void formulaTemElegibilidadeExataSemNullIgualZeroOuArredondamentoInventado() {
        assertThat(LinxFichaCustosService.MATERIAIS_SQL)
            .contains("MS.GRUPO = M.GRUPO AND MS.SUBGRUPO = M.SUBGRUPO",
                "WHEN MS.IGNORAR_EM_FORMACAO_PRECO = 1", "M.SEMI_ACABADO = 1",
                "MS.IGNORAR_EM_FORMACAO_PRECO IS NULL", "MC.CUSTO_A_VISTA IS NULL",
                "M.FATOR_CONVERSAO = 0", "VC.PORCENTAGEM_CONSUMO IS NULL",
                "X.CONSUMO * MC.CUSTO_A_VISTA / NULLIF(M.FATOR_CONVERSAO, 0)",
                "* VC.PORCENTAGEM_CONSUMO / 100", "SEM_TAMANHO", "PENDENTE_DADOS", "PENDENTE_SEMIACABADO")
            .doesNotContain("COALESCE", "ISNULL(", "ROUND(", "M.SEMI_ACABADO IS NULL", "V.C10", "UPDATE", "INSERT", "DELETE");
    }

    @Test void itemReutilizadoNaoTemCustoEstimadoEExplicaAVinculacaoAmbigua() {
        var row = new LinkedHashMap<String, Object>();
        row.put("ITEM", "0002");
        row.put("MATERIAL", "01001");
        row.put("ITEM_REUTILIZADO", true);
        row.put("STATUS_CALCULO", "PENDENTE_VINCULO_ITEM");
        row.put("PRECO_COR_A_VISTA", new BigDecimal("12.34"));
        row.put("CONSUMO", new BigDecimal("0.8"));
        row.put("CUSTO_CONSUMO_LINX", null);
        when(jdbc.queryForList(eq(LinxFichaCustosService.MATERIAIS_SQL), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(row));
        var result = read();
        assertThat(result.materiais().getFirst()).containsEntry("CUSTO_CONSUMO_LINX", null)
            .containsEntry("ITEM_REUTILIZADO", true).containsEntry("PRECO_COR_A_VISTA", new BigDecimal("12.34"));
        assertThat(result.avisos()).anyMatch(text -> text.startsWith("Há item reutilizado"));
        assertThat(LinxFichaCustosService.MATERIAIS_SQL)
            .contains("V2.ITEM = V.ITEM AND V2.MATERIAL <> V.MATERIAL",
                "WHEN J.ITEM_REUTILIZADO = 1 THEN 'PENDENTE_VINCULO_ITEM'",
                "CASE WHEN ST.STATUS_CALCULO = 'CALCULADO'")
            .contains("VC.ITEM = V.ITEM AND VC.MATERIAL = V.MATERIAL");
    }

    @Test void todasAsFontesUsamParametrosENaoMisturamProdutoOuVersao() {
        read();
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(7)).queryForList(sql.capture(), params.capture());
        assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query)
            .contains(":produto").doesNotContain("TESTE", "UPDATE ", "INSERT ", "DELETE ", "MERGE "));
        assertThat(params.getAllValues()).allSatisfy(param -> {
            assertThat(param.getValue("produto")).isEqualTo("TESTE");
            assertThat(param.getValue("versao")).isEqualTo("00002");
        });
        assertThat(sql.getAllValues().get(1)).contains("V.VERSAO_FICHA = :versao", "VC.VERSAO_FICHA = V.VERSAO_FICHA");
    }

    @Test void diagnosticoExternoNuncaEExecutadoMasFontesObrigatoriasPermanecem() {
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
            .thenReturn(Map.of("OBJETO_VALIDO", 1, "DEPENDENCIAS_INSEGURAS", 2));
        var result = read();
        assertThat(result.diagnostico()).isEmpty();
        assertThat(result.avisos()).anyMatch(text -> text.contains("dependência externa"));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(6)).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getAllValues()).noneMatch(query -> query.contains("FROM dbo.W_KING_PRODUTO_CUSTO_FICHA_SINTETICO"));
    }

    @Test void metadadosAusentesOuSemPermissaoBloqueiamSomenteDiagnostico() {
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
            .thenThrow(new DataAccessResourceFailureException("Metadados indisponíveis"));
        var result = read();
        assertThat(result.diagnostico()).isEmpty();
        assertThat(result.avisos()).anyMatch(text -> text.contains("Não foi possível validar"));
    }

    @Test void guardRecursivoBloqueiaSynonymsRemotosModulosOpacosENaoResolvidos() {
        assertThat(LinxFichaCustosService.DIAGNOSTICO_GUARD_SQL)
            .contains("referenced_server_name IS NOT NULL", "referenced_database_name <> DB_NAME()",
                "O.type IN ('SN', 'FS', 'FT', 'AF')", "M.definition IS NULL", "OPENQUERY", "OPENROWSET",
                "OPENDATASOURCE", "E.referenced_id IS NULL", "VIEW DEFINITION", "MAXRECURSION 50");
    }

    @Test void falhaDeFonteObrigatoriaNaoViraRespostaParcialSilenciosa() {
        when(jdbc.queryForList(eq(LinxFichaCustosService.MATERIAIS_SQL), any(MapSqlParameterSource.class)))
            .thenThrow(new DataAccessResourceFailureException("Fonte obrigatória indisponível"));
        assertThatThrownBy(this::read).isInstanceOf(DataAccessResourceFailureException.class);
        verify(jdbc, never()).queryForMap(anyString(), any(MapSqlParameterSource.class));
    }

    @Test void fichaDeOutraColecaoNaoPodeLiberarFontesDeCustos() {
        when(fichas.getDetalhe(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new ResourceNotFoundException("Ficha de outra coleção"));
        assertThatThrownBy(this::read).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void bancoIncorretoBloqueiaAntesDeQualquerLeituraDeCustos() {
        when(integration.requireExpectedDatabase()).thenThrow(new BusinessRuleException("Banco incorreto"));
        assertThatThrownBy(this::read).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(jdbc, fichas);
    }

    @Test void versaoInvalidaNoLegadoNaoDisparaBuscaDaFichaAtual() {
        assertThatThrownBy(() -> service.getCustos("TESTE", "000001", "KING&JOE", "025", "Coleção teste", true))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.getCustos("TESTE", " ", "KING&JOE", "025", "Coleção teste", true))
            .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(jdbc, integration, fichas);
    }

    @Test void legadoAdmiteApenasAVersaoAtualValidadaSemAtribuirHistoricoAColecao() {
        var legacy = new LinxFichaVersaoDTO("TESTE", "00001", null, null, null, null, null, null);
        when(fichas.getAtual("TESTE", "KING&JOE", "025", "Coleção teste"))
            .thenReturn(new LinxFichaDetalheDTO(legacy, List.of()));
        assertThatThrownBy(() -> service.getCustos("TESTE", "00002", "KING&JOE", "025", "Coleção teste", true))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(jdbc);
        var result = service.getCustos("TESTE", "00001", "KING&JOE", "025", "Coleção teste", true);
        assertThat(result.versao().colecaoCodigo()).isNull();
        assertThat(result.avisos()).anyMatch(text -> text.contains("Ficha legada sem U_COLECAO"));
        verify(fichas, never()).getDetalhe(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
