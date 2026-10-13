package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import com.modaflow.backend.peca.PecaRepository;
import com.modaflow.backend.peca.EtapaPecaEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinxIntegrationServiceTest {

    @Mock
    private LinxDatabaseClient databaseClient;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private PecaRepository pecaRepository;

    private LinxIntegrationService service;

    @BeforeEach
    void setUp() {
        when(databaseClient.jdbc()).thenReturn(jdbc);
        service = spy(new LinxIntegrationService(databaseClient, pecaRepository));
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pesquisaFornecedorComLimiteEMapeiaNomeECodigoJuntos() throws Exception {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        ResultSet row = mock(ResultSet.class);
        when(row.getString("FORNECEDOR")).thenReturn(" VICUNHA  ");
        when(row.getString("CODIGO")).thenReturn(" 000123 ");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(2)).mapRow(row, 0)));

        String search = "  000123%' OR 1=1--  ";
        assertThat(service.getFornecedores(search))
            .containsExactly(new LinxFornecedorDTO("VICUNHA", "000123"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("TOP (50)", "CHARINDEX(:search, FORNECEDOR)", "CHARINDEX(:search, COD_FORNECEDOR)")
            .contains("AND INATIVO = 0", "dbo.CADASTRO_CLI_FOR", "CF.INATIVO = 0")
            .doesNotContain(search.trim());
        assertThat(params.getValue().getValue("search")).isEqualTo(search.trim());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void consultaZiperSemConverterDimensoesVarcharVaziasEmDecimal() throws Exception {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        ResultSet row = mock(ResultSet.class);
        when(row.getString("CODIGO")).thenReturn("02160210 ");
        when(row.getString("NOME")).thenReturn("ZIP NYLON SEP DESL 60CM PUX COR DO ZIPER ");
        when(row.getString("GRUPO")).thenReturn("AVIAMENTOS");
        when(row.getString("UNIDADE_ESTOQUE")).thenReturn("UN");
        when(row.getString("UNIDADE_FICHA_TECNICA")).thenReturn("UN");
        when(row.getString("LARGURA")).thenReturn("  ");
        when(row.getString("COMPRIMENTO")).thenReturn("");
        when(row.getBigDecimal("RENDIMENTO")).thenReturn(null);
        when(row.getBigDecimal("CUSTO_A_VISTA")).thenReturn(BigDecimal.ZERO);
        when(row.getBigDecimal("CUSTO_REPOSICAO")).thenReturn(new BigDecimal("3.14250"));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(2)).mapRow(row, 0)));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(java.util.Map.of("CODIGO_COR", "00003", "NOME_COR", "PRETO",
                    "CUSTO_A_VISTA", new BigDecimal("3.00000"))),
                List.of(java.util.Map.of("CODIGO_COR", "00003", "CODIGO_FORNECEDOR", "000123",
                    "CUSTO_REPOSICAO", new BigDecimal("3.14250"), "UNIDADE", "UN")));

        LinxMaterialCadastroDTO result = service.getMaterialCadastro(" 02160210 ");

        assertThat(result.codigo()).isEqualTo("02160210");
        assertThat(result.grupo()).isEqualTo("AVIAMENTOS");
        assertThat(result.unidadeFichaTecnica()).isEqualTo("UN");
        assertThat(result.largura()).isNull();
        assertThat(result.comprimento()).isNull();
        assertThat(result.altura()).isNull();
        assertThat(result.cores().getFirst().get("CODIGO_COR")).isEqualTo("00003");
        assertThat(result.custoAVista()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.custoReposicao()).isEqualByComparingTo("3.14250");
        assertThat(result.fornecedores().getFirst()).containsEntry("CODIGO_FORNECEDOR", "000123")
            .containsEntry("CUSTO_REPOSICAO", new BigDecimal("3.14250"));
        verify(row, never()).getBigDecimal("LARGURA");
        verify(row, never()).getBigDecimal("COMPRIMENTO");
        verify(row, never()).getBigDecimal("ALTURA");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<String> baseSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(baseSql.capture(), params.capture(), any(RowMapper.class));
        assertThat(baseSql.getValue()).contains("M.INATIVO = 0", "M.CUSTO_A_VISTA", "M.CUSTO_REPOSICAO");
        assertThat(params.getValue().getValue("codigo")).isEqualTo("02160210");
        ArgumentCaptor<String> vinculosSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForList(vinculosSql.capture(), any(MapSqlParameterSource.class));
        assertThat(vinculosSql.getAllValues().getFirst()).contains("C.INATIVO = 0", "C.CUSTO_REPOSICAO");
        assertThat(vinculosSql.getAllValues().getLast()).contains("MF.MATERIAL = :codigo",
            "C.INATIVO = 0", "F.INATIVO = 0", "CF.INATIVO = 0", "MF.UNIDADE", "MF.FATOR_CONVERSAO_FORNECEDOR")
            .doesNotContain("MF.INATIVO", "TOP (50)");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void naoRetornaCoresOuFornecedoresQuandoNaoHaMaterialAtivo() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of())).when(service).getConnectionInfo();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        assertThatThrownBy(() -> service.getMaterialCadastro("01010001"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Nenhum material ativo");
        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listaPrimeirasCoresSemBuscaEConsultaTermoNoBanco() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of("AZUL"));
        assertThat(service.getCoresBasicas(null)).containsExactly("AZUL");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("TOP (50)", "dbo.CORES_BASICAS", "CHARINDEX(:search, DESC_COR)");
        assertThat(params.getValue().getValue("search")).isEqualTo("");
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
    void listaColecoesSomenteComGriffeDefinidaEDoAnoAtualEmDiante() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenReturn(List.of());

        assertThat(service.getColecoesValidas(List.of("K&J BLACK"), null)).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
            ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));

        assertThat(sql.getValue()).contains(
            "NULLIF(RTRIM(LTRIM(C.GRIFFE)), '') IS NOT NULL",
            "UPPER(RTRIM(LTRIM(P.GRUPO_PRODUTO))) = 'ROUPAS'",
            "PATINDEX('%20[0-9][0-9]%', C.DESC_COLECAO)",
            ">= :anoInicialColecao",
            "RTRIM(LTRIM(C.GRIFFE)) IN (:griffes)"
        ).doesNotContain(
            "C.DATA_FINAL_META >=",
            "P.GRIFFE IN",
            "P.GRIFFE, C.GRIFFE"
        );
        assertThat(parameters.getValue().getValue("anoInicialColecao"))
            .isEqualTo(LocalDate.now().getYear());
        assertThat(parameters.getValue().getValue("griffes")).isEqualTo(List.of("K&J BLACK"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void vinculaSomenteRoupasAtivasPeloCodigoEGriffeDaColecao() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class)
        )).thenReturn(List.of());

        assertThat(service.getProdutosDaColecao("212", "KING&JOE PLAY")).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
            ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));

        assertThat(sql.getValue()).contains(
            "C.COLECAO = P.COLECAO",
            "UPPER(RTRIM(LTRIM(P.GRUPO_PRODUTO))) = 'ROUPAS'",
            "RTRIM(LTRIM(C.COLECAO)) = :codigoColecao",
            "RTRIM(LTRIM(C.GRIFFE)) IN (:griffes)"
        );
        assertThat(parameters.getValue().getValue("codigoColecao")).isEqualTo("212");
        assertThat(parameters.getValue().getValue("griffes"))
            .isEqualTo(List.of("KING&JOE PLAY"));
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
        assertThat(parameters.getValue().getValue("tipos")).isEqualTo(List.of("BERMUDA"));
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listaModelagensPelosCodigosDeReferenciaSemFiltrarGriffe() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of("CA4475J.2"));

        assertThat(service.getModelagens()).containsExactly("CA4475J.2");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue())
            .contains("FROM dbo.PRODUTOS", "LTRIM(PRODUTO)")
            .doesNotContain("GRIFFE", "COLECAO");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listaTodasCoresMostruarioDaTabelaCoresBasicas() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of("AZUL"));

        assertThat(service.getCoresMostruario()).containsExactly("AZUL");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue())
            .contains("FROM dbo.CORES_BASICAS", "LTRIM(DESC_COR)")
            .doesNotContain("TOP (50)", "MATERIAIS_CORES");
    }

    @Test
    @SuppressWarnings("unchecked")
    void retornaPaginaVaziaQuandoFiltroOperacionalNaoEncontraCodigoNoModaFlow() {
        doReturn(connectionInfo("HOMOLOGACAO_COBRANCA", true, List.of()))
            .when(service).getConnectionInfo();
        when(pecaRepository.findAll(any(Specification.class))).thenReturn(List.of());

        LinxProdutosValidosPageResponseDTO result = service.getProdutosValidos(
            0,
            50,
            null,
            null,
            null,
            null,
            null,
            List.of(EtapaPecaEnum.GERACAO_FICHA),
            List.of(),
            List.of()
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.sourceDatabase()).isEqualTo("HOMOLOGACAO_COBRANCA");
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
