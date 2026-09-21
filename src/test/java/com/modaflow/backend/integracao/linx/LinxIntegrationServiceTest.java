package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinxIntegrationServiceTest {

    @Mock
    private LinxDatabaseClient databaseClient;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private LinxIntegrationService service;

    @BeforeEach
    void setUp() {
        when(databaseClient.jdbc()).thenReturn(jdbc);
        service = spy(new LinxIntegrationService(databaseClient));
    }

    @Test
    void bloqueiaConsultaQuandoConexaoNaoEstaNaHomologacaoCobranca() {
        doReturn(connectionInfo("OUTRO_BANCO", false, List.of("PRODUTOS:UPDATE")))
            .when(service).getConnectionInfo();

        assertThatThrownBy(() -> service.getProdutosValidos(0, 50, null, null, null, null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("HOMOLOGACAO_COBRANCA")
            .hasMessageContaining("OUTRO_BANCO");
    }

    @Test
    void bloqueiaConsultaQuandoCredencialPossuiEscritaEExcecaoNaoFoiHabilitada() {
        doReturn(connectionInfo(
            "HOMOLOGACAO_COBRANCA",
            false,
            List.of("PRODUTOS:UPDATE")
        )).when(service).getConnectionInfo();
        when(databaseClient.allowWriteCapableCredential()).thenReturn(false);

        assertThatThrownBy(() -> service.getColecoesValidas(null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("somente SELECT")
            .hasMessageContaining("PRODUTOS:UPDATE");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void permiteConsultaNoBancoObrigatorioQuandoCredencialPossuiEscrita() {
        doReturn(connectionInfo(
            "HOMOLOGACAO_COBRANCA",
            false,
            List.of("PRODUTOS:UPDATE")
        )).when(service).getConnectionInfo();
        when(databaseClient.allowWriteCapableCredential()).thenReturn(true);
        when(jdbc.query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenReturn(List.of());

        assertThat(service.getColecoesValidas(null, null)).isEmpty();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void auditaPermissoesEfetivasEmTodasAsTabelasConsultadas() throws Exception {
        when(databaseClient.expectedDatabase()).thenReturn("HOMOLOGACAO_COBRANCA");
        ResultSet connectionResult = mock(ResultSet.class);
        when(connectionResult.getString("SERVIDOR")).thenReturn("SRV-LINX");
        when(connectionResult.getString("BANCO")).thenReturn("HOMOLOGACAO_COBRANCA");
        when(jdbc.queryForObject(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(2);
            return mapper.mapRow(connectionResult, 0);
        });
        when(jdbc.query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenReturn(List.of());

        LinxConnectionInfoDTO result = service.getConnectionInfo();

        assertThat(result.bancoEsperadoConfirmado()).isTrue();
        assertThat(result.credencialSomenteLeitura()).isTrue();
        assertThat(result.integracaoSomenteLeitura()).isTrue();
        assertThat(result.permissoesEscritaDetectadas()).isEmpty();

        ArgumentCaptor<String> permissionSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            permissionSql.capture(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        );
        assertThat(permissionSql.getValue()).contains(
            "dbo.PRODUTOS",
            "dbo.COLECOES",
            "dbo.MATERIAIS",
            "dbo.PRODUTOS_SUBGRUPO",
            "dbo.PRODUTOS_LINHAS",
            "dbo.PRODUTOS_TAMANHOS",
            "dbo.PRODUTOS_CATEGORIA"
        );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listaProdutoVinculadoSemAplicarStatusOuGriffeDaColecaoComoFiltro() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.queryForObject(
            anyString(),
            any(MapSqlParameterSource.class),
            eq(Long.class)
        )).thenReturn(1L);

        LocalDateTime cadastramento = LocalDateTime.of(2025, 6, 2, 16, 11, 39);
        LinxProdutoValidoResponseDTO product = new LinxProdutoValidoResponseDTO(
            6449L,
            "BM.020.01J",
            "BM02001J",
            "BERMUDA CASUAL CETIM FLEX",
            "K&J BLACK",
            "402",
            "02 - INVERNO 2026 K&J BLACK",
            null,
            LocalDateTime.of(2025, 10, 1, 0, 0),
            LocalDateTime.of(2026, 6, 30, 0, 0),
            "4",
            "ROUPAS",
            "BERMUDA",
            "NORMAL",
            "1 SEMESTRE 2026",
            "38-48",
            "INDEFINIDO",
            "89",
            "01050089",
            "CETIM FLEX",
            null,
            "WESLEY.RODRIGUES",
            null,
            "70",
            "3",
            cadastramento,
            null,
            new BigDecimal("48.77"),
            new BigDecimal("0.00")
        );
        when(jdbc.query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenReturn(List.of(product));

        LinxProdutosValidosPageResponseDTO result =
            service.getProdutosValidos(0, 50, null, null, null, null, null);

        assertThat(result.sourceDatabase()).isEqualTo("HOMOLOGACAO_COBRANCA");
        assertThat(result.readOnly()).isTrue();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.linxId()).isEqualTo(6449L);
            assertThat(item.codigo()).isEqualTo("BM.020.01J");
            assertThat(item.codigoNormalizado()).isEqualTo("BM02001J");
            assertThat(item.colecaoCodigo()).isEqualTo("402");
            assertThat(item.griffeColecao()).isNull();
        });

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(
            countSql.capture(),
            any(MapSqlParameterSource.class),
            eq(Long.class)
        );
        ArgumentCaptor<String> dataSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            dataSql.capture(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        );

        assertThat(countSql.getValue()).contains("C.COLECAO = P.COLECAO", "P.GRIFFE IN");
        assertThat(dataSql.getValue()).contains("P.STATUS_PRODUTO", "OFFSET :offset");
        assertThat(dataSql.getValue()).doesNotContain(
            "P.STATUS_PRODUTO =",
            "P.TIPO_STATUS_PRODUTO =",
            "C.GRIFFE = P.GRIFFE",
            "DATA_FINAL_META <="
        );
    }

    @Test
    void rejeitaGriffeForaDoEscopo() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();

        assertThatThrownBy(() -> service.getProdutosValidos(0, 50, null, "OUTRA", null, null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Griffe inválida");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aplicaTipoEMaterialNoServidorComParametros() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.queryForObject(
            anyString(),
            any(MapSqlParameterSource.class),
            eq(Long.class)
        )).thenReturn(0L);
        when(jdbc.query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenReturn(List.of());

        service.getProdutosValidos(0, 50, null, null, null, "BERMUDA", "CETIM_100%");

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
            ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(countSql.capture(), parameters.capture(), eq(Long.class));

        assertThat(countSql.getValue())
            .contains("P.SUBGRUPO_PRODUTO", "dbo.MATERIAIS AS MF")
            .doesNotContain("P.TIPO_PRODUTO")
            .doesNotContain("C.GRIFFE = P.GRIFFE");
        assertThat(parameters.getValue().getValue("tipo")).isEqualTo("BERMUDA");
        assertThat(parameters.getValue().getValue("material")).isEqualTo("%CETIM\\_100\\%%");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listaSomenteSubgruposAtivosDoGrupoRoupas() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenReturn(List.of("BERMUDA"));

        assertThat(service.getSubgrupos()).containsExactly("BERMUDA");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
            sql.capture(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        );
        assertThat(sql.getValue()).contains(
            "FROM dbo.PRODUTOS_SUBGRUPO",
            "GRUPO_PRODUTO = 'ROUPAS'",
            "ISNULL(INATIVO, 0) = 0"
        );
    }

    private LinxConnectionInfoDTO connectionInfo(
        String database,
        boolean readOnly,
        List<String> writePermissions
    ) {
        return new LinxConnectionInfoDTO(
            "SQL-01",
            database,
            "HOMOLOGACAO_COBRANCA",
            "HOMOLOGACAO_COBRANCA".equals(database),
            readOnly,
            true,
            writePermissions
        );
    }
}
