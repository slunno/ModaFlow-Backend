package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.colecao.StatusColecaoEnum;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.modaflow.backend.peca.Peca;
import com.modaflow.backend.peca.PecaRepository;
import com.modaflow.backend.peca.StatusPecaEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Consultas nativas, parametrizadas e exclusivamente de leitura no Linx.
 * Este serviço usa um pool separado do EntityManager/JPA do ModaFlow.
 */
@Service
@ConditionalOnProperty(prefix = "modaflow.linx", name = "enabled", havingValue = "true")
public class LinxIntegrationService {

    private static final String PRODUTOS_VALIDOS_FROM_WHERE = """
        FROM dbo.PRODUTOS AS P
        INNER JOIN dbo.COLECOES AS C ON C.COLECAO = P.COLECAO
          OR (C.GRIFFE = P.GRIFFE AND EXISTS (SELECT 1 FROM dbo.PRODUTO_FICHA_VERSAO H
              WHERE H.PRODUTO=P.PRODUTO AND H.U_COLECAO=C.COLECAO))
        WHERE NULLIF(RTRIM(LTRIM(P.PRODUTO)), '') IS NOT NULL
          AND NULLIF(RTRIM(LTRIM(C.COLECAO)), '') IS NOT NULL
          AND ISNULL(P.INATIVO, 0) = 0
          AND ISNULL(C.INATIVO, 0) = 0
          AND P.GRIFFE IN ('K&J BLACK', 'KING&JOE', 'KING&JOE PLAY')
        """;

    /**
     * A listagem de coleções é definida pela griffe cadastrada na própria coleção,
     * não pela griffe de cada produto vinculado a ela.
     */
    private static final String COLECOES_VALIDAS_FROM_WHERE = """
        FROM dbo.PRODUTOS AS P
        INNER JOIN dbo.COLECOES AS C ON C.COLECAO = P.COLECAO
          OR (C.GRIFFE = P.GRIFFE AND EXISTS (SELECT 1 FROM dbo.PRODUTO_FICHA_VERSAO H
              WHERE H.PRODUTO=P.PRODUTO AND H.U_COLECAO=C.COLECAO))
        WHERE NULLIF(RTRIM(LTRIM(P.PRODUTO)), '') IS NOT NULL
          AND NULLIF(RTRIM(LTRIM(C.COLECAO)), '') IS NOT NULL
          AND ISNULL(P.INATIVO, 0) = 0
          AND ISNULL(C.INATIVO, 0) = 0
          AND UPPER(RTRIM(LTRIM(P.GRUPO_PRODUTO))) = 'ROUPAS'
          AND NULLIF(RTRIM(LTRIM(C.GRIFFE)), '') IS NOT NULL
          AND RTRIM(LTRIM(C.GRIFFE)) IN ('K&J BLACK', 'KING&JOE', 'KING&JOE PLAY')
        """;

    private static final String CONNECTION_INFO_SQL = """
        SELECT
            CONVERT(varchar(128), SERVERPROPERTY('ServerName')) AS SERVIDOR,
            CONVERT(varchar(128), DB_NAME()) AS BANCO
        """;

    private static final String WRITE_PERMISSIONS_SQL = """
        SELECT P.OBJETO, P.PERMISSAO
        FROM (
            VALUES
                ('dbo.PRODUTOS', 'INSERT'),
                ('dbo.PRODUTOS', 'UPDATE'),
                ('dbo.PRODUTOS', 'DELETE'),
                ('dbo.PRODUTOS', 'ALTER'),
                ('dbo.COLECOES', 'INSERT'),
                ('dbo.COLECOES', 'UPDATE'),
                ('dbo.COLECOES', 'DELETE'),
                ('dbo.COLECOES', 'ALTER'),
                ('dbo.MATERIAIS', 'INSERT'),
                ('dbo.MATERIAIS', 'UPDATE'),
                ('dbo.MATERIAIS', 'DELETE'),
                ('dbo.MATERIAIS', 'ALTER'),
                ('dbo.PRODUTOS_SUBGRUPO', 'INSERT'),
                ('dbo.PRODUTOS_SUBGRUPO', 'UPDATE'),
                ('dbo.PRODUTOS_SUBGRUPO', 'DELETE'),
                ('dbo.PRODUTOS_SUBGRUPO', 'ALTER'),
                ('dbo.PRODUTOS_LINHAS', 'INSERT'),
                ('dbo.PRODUTOS_LINHAS', 'UPDATE'),
                ('dbo.PRODUTOS_LINHAS', 'DELETE'),
                ('dbo.PRODUTOS_LINHAS', 'ALTER'),
                ('dbo.PRODUTOS_TAMANHOS', 'INSERT'),
                ('dbo.PRODUTOS_TAMANHOS', 'UPDATE'),
                ('dbo.PRODUTOS_TAMANHOS', 'DELETE'),
                ('dbo.PRODUTOS_TAMANHOS', 'ALTER'),
                ('dbo.PRODUTOS_CATEGORIA', 'INSERT'),
                ('dbo.PRODUTOS_CATEGORIA', 'UPDATE'),
                ('dbo.PRODUTOS_CATEGORIA', 'DELETE'),
                ('dbo.PRODUTOS_CATEGORIA', 'ALTER')
        ) AS P(OBJETO, PERMISSAO)
        WHERE ISNULL(HAS_PERMS_BY_NAME(P.OBJETO, 'OBJECT', P.PERMISSAO), 1) = 1
        ORDER BY P.OBJETO, P.PERMISSAO
        """;

    private final LinxDatabaseClient databaseClient;
    private final NamedParameterJdbcTemplate jdbc;
    private final PecaRepository pecaRepository;

    public LinxIntegrationService(
        LinxDatabaseClient databaseClient,
        PecaRepository pecaRepository
    ) {
        this.databaseClient = databaseClient;
        this.jdbc = databaseClient.jdbc();
        this.pecaRepository = pecaRepository;
    }

    /** Informa o banco e audita as permissões efetivas da credencial SQL. */
    public LinxConnectionInfoDTO getConnectionInfo() {
        ConnectionIdentity identity = jdbc.queryForObject(
            CONNECTION_INFO_SQL,
            new MapSqlParameterSource(),
            (rs, rowNum) -> new ConnectionIdentity(
                clean(rs.getString("SERVIDOR")),
                clean(rs.getString("BANCO"))
            )
        );
        List<String> writePermissions = jdbc.query(
            WRITE_PERMISSIONS_SQL,
            new MapSqlParameterSource(),
            (rs, rowNum) -> clean(rs.getString("OBJETO")) + ":" + clean(rs.getString("PERMISSAO"))
        );
        String database = identity.database();
        return new LinxConnectionInfoDTO(
            identity.server(),
            database,
            databaseClient.expectedDatabase(),
            databaseClient.expectedDatabase().equalsIgnoreCase(database),
            writePermissions.isEmpty(),
            true,
            List.copyOf(writePermissions)
        );
    }

    /** Lista, com paginação, produtos oficiais do Linx ligados a uma coleção. */
    public LinxProdutosValidosPageResponseDTO getProdutosValidos(
        int page,
        int size,
        String search,
        String griffe,
        String colecao,
        String tipo,
        String material
    ) {
        return getProdutosValidos(
            page,
            size,
            search,
            singletonOrEmpty(griffe),
            singletonOrEmpty(colecao),
            singletonOrEmpty(tipo),
            material,
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Cruza os filtros operacionais do ModaFlow com a fonte oficial do Linx.
     * Os códigos são validados no Linx antes de integrarem a página retornada.
     */
    public LinxProdutosValidosPageResponseDTO getProdutosValidos(
        int page,
        int size,
        String search,
        List<String> griffes,
        List<String> colecoes,
        List<String> tipos,
        String material,
        List<EtapaPecaEnum> etapas,
        List<LinxStatusPecaFiltro> statusPecas,
        List<StatusColecaoEnum> statusColecoes
    ) {
        LinxConnectionInfoDTO connection = requireExpectedDatabase();
        List<String> griffeFilters = normalizeGriffeFilters(griffes);
        String searchFilter = blankToNull(search);
        List<String> colecaoFilters = normalizeTextFilters(colecoes);
        List<String> tipoFilters = normalizeTextFilters(tipos).stream()
            .map(value -> value.toUpperCase(Locale.ROOT))
            .toList();
        String materialFilter = blankToNull(material);

        StringBuilder filters = new StringBuilder(PRODUTOS_VALIDOS_FROM_WHERE);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (!griffeFilters.isEmpty()) {
            filters.append(" AND RTRIM(LTRIM(P.GRIFFE)) IN (:griffes)");
            params.addValue("griffes", griffeFilters);
        }
        if (!colecaoFilters.isEmpty()) {
            filters.append(" AND RTRIM(LTRIM(C.COLECAO)) IN (:colecoes)");
            params.addValue("colecoes", colecaoFilters);
        }
        if (searchFilter != null) {
            filters.append("""
                 AND (
                     UPPER(CONVERT(varchar(300), ISNULL(P.DESC_PRODUTO, ''))) LIKE :search ESCAPE '\\'
                     OR UPPER(RTRIM(LTRIM(P.PRODUTO))) LIKE :search ESCAPE '\\'
                     OR REPLACE(REPLACE(UPPER(P.PRODUTO), '.', ''), ' ', '') LIKE :searchCode ESCAPE '\\'
                 )
                """);
            String escaped = escapeLike(searchFilter.toUpperCase(Locale.ROOT));
            String normalizedCode = searchFilter.toUpperCase(Locale.ROOT).replace(".", "").replace(" ", "");
            params.addValue("search", "%" + escaped + "%");
            params.addValue("searchCode", "%" + escapeLike(normalizedCode) + "%");
        }
        if (!tipoFilters.isEmpty()) {
            filters.append(" AND UPPER(RTRIM(LTRIM(P.SUBGRUPO_PRODUTO))) IN (:tipos)");
            params.addValue("tipos", tipoFilters);
        }
        if (materialFilter != null) {
            filters.append("""
                 AND (
                     UPPER(CONVERT(varchar(100), ISNULL(P.MATERIAL, ''))) LIKE :material ESCAPE '\\'
                     OR EXISTS (
                         SELECT 1
                         FROM dbo.MATERIAIS AS MF
                         WHERE MF.MATERIAL = P.MATERIAL
                           AND UPPER(CONVERT(varchar(300), ISNULL(MF.DESC_MATERIAL, '')))
                               LIKE :material ESCAPE '\\'
                     )
                 )
                """);
            params.addValue(
                "material",
                "%" + escapeLike(materialFilter.toUpperCase(Locale.ROOT)) + "%"
            );
        }

        boolean hasWorkflowFilter = !safeList(etapas).isEmpty()
            || !safeList(statusPecas).isEmpty()
            || !safeList(statusColecoes).isEmpty();
        long totalElements;
        String workflowPageFilter = "";
        String pagination;

        if (hasWorkflowFilter) {
            Set<String> workflowCodes = findWorkflowProductCodes(etapas, statusPecas, statusColecoes);
            if (workflowCodes.isEmpty()) {
                return emptyPage(page, size, connection.banco());
            }

            String keysSql = """
                SELECT
                    P.ID,
                    REPLACE(REPLACE(UPPER(RTRIM(LTRIM(P.PRODUTO))), '.', ''), ' ', '') AS CODIGO_NORMALIZADO
                """ + filters + """
                 ORDER BY P.GRIFFE, C.COLECAO, P.PRODUTO, P.ID
                """;
            List<LinxProductKey> matchingKeys = jdbc.query(
                keysSql,
                params,
                (rs, rowNum) -> new LinxProductKey(
                    nullableLong(rs, "ID"),
                    clean(rs.getString("CODIGO_NORMALIZADO"))
                )
            ).stream()
                .filter(key -> key.id() != null && workflowCodes.contains(key.normalizedCode()))
                .toList();

            totalElements = matchingKeys.size();
            long fromIndexLong = (long) page * size;
            if (fromIndexLong >= totalElements) {
                return emptyPage(page, size, connection.banco(), totalElements);
            }
            int fromIndex = Math.toIntExact(fromIndexLong);
            int toIndex = (int) Math.min(fromIndexLong + size, totalElements);
            List<Long> pageIds = matchingKeys.subList(fromIndex, toIndex).stream()
                .map(LinxProductKey::id)
                .toList();
            params.addValue("workflowPageIds", pageIds);
            workflowPageFilter = " AND P.ID IN (:workflowPageIds)";
            pagination = "";
        } else {
            Long count = jdbc.queryForObject("SELECT COUNT_BIG(*) " + filters, params, Long.class);
            totalElements = count == null ? 0 : count;
            long offset = (long) page * size;
            params.addValue("offset", offset);
            params.addValue("size", size);
            pagination = " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
        }

        String dataSql = """
            SELECT
                P.ID,
                RTRIM(LTRIM(P.PRODUTO)) AS CODIGO,
                REPLACE(REPLACE(UPPER(RTRIM(LTRIM(P.PRODUTO))), '.', ''), ' ', '') AS CODIGO_NORMALIZADO,
                RTRIM(LTRIM(P.DESC_PRODUTO)) AS DESCRICAO,
                RTRIM(LTRIM(P.GRIFFE)) AS GRIFFE,
                RTRIM(LTRIM(C.COLECAO)) AS COLECAO_CODIGO,
                RTRIM(LTRIM(C.DESC_COLECAO)) AS COLECAO_NOME,
                NULLIF(RTRIM(LTRIM(C.GRIFFE)), '') AS GRIFFE_COLECAO,
                C.DATA_INICIO_META,
                C.DATA_FINAL_META,
                RTRIM(LTRIM(CONVERT(varchar(50), C.TEMPORADA))) AS TEMPORADA,
                RTRIM(LTRIM(P.GRUPO_PRODUTO)) AS GRUPO_PRODUTO,
                RTRIM(LTRIM(P.SUBGRUPO_PRODUTO)) AS SUBGRUPO_PRODUTO,
                RTRIM(LTRIM(P.TIPO_PRODUTO)) AS TIPO_PRODUTO,
                RTRIM(LTRIM(P.PERIODO_PCP)) AS PERIODO_PCP,
                RTRIM(LTRIM(P.GRADE)) AS GRADE,
                RTRIM(LTRIM(P.LINHA)) AS LINHA,
                RTRIM(LTRIM(P.CARTELA)) AS CARTELA,
                RTRIM(LTRIM(P.MATERIAL)) AS MATERIAL,
                (
                    SELECT MIN(RTRIM(LTRIM(M.DESC_MATERIAL)))
                    FROM dbo.MATERIAIS AS M
                    WHERE M.MATERIAL = P.MATERIAL
                ) AS DESC_MATERIAL,
                RTRIM(LTRIM(P.MODELAGEM)) AS MODELAGEM,
                RTRIM(LTRIM(P.ESTILISTA)) AS ESTILISTA,
                RTRIM(LTRIM(P.MODELISTA)) AS MODELISTA,
                RTRIM(LTRIM(CONVERT(varchar(30), P.STATUS_PRODUTO))) AS STATUS_PRODUTO,
                RTRIM(LTRIM(CONVERT(varchar(30), P.TIPO_STATUS_PRODUTO))) AS TIPO_STATUS_PRODUTO,
                P.DATA_CADASTRAMENTO,
                P.DATA_INICIO_DESENVOLVIMENTO,
                P.CUSTO_REPOSICAO1,
                P.PRECO_REPOSICAO_1
            """ + filters + workflowPageFilter + """
             ORDER BY P.GRIFFE, C.COLECAO, P.PRODUTO, P.ID
            """ + pagination;

        List<LinxProdutoValidoResponseDTO> items = jdbc.query(dataSql, params, this::mapProdutoValido);
        int totalPages = calculateTotalPages(totalElements, size);

        return new LinxProdutosValidosPageResponseDTO(
            items,
            page,
            size,
            totalElements,
            totalPages,
            (long) page + 1 < totalPages,
            connection.banco(),
            true
        );
    }

    /** Retorna coleções que contêm pelo menos um produto elegível. */
    public List<LinxColecaoValidaResponseDTO> getColecoesValidas(List<String> griffes, String search) {
        requireExpectedDatabase();
        List<String> griffeFilters = normalizeGriffeFilters(griffes);
        String searchFilter = blankToNull(search);
        StringBuilder filters = new StringBuilder(COLECOES_VALIDAS_FROM_WHERE);
        MapSqlParameterSource params = new MapSqlParameterSource();
        int anoAtual = LocalDate.now().getYear();

        // O corte considera o ano comercial escrito no nome da coleção. Uma coleção
        // "VERÃO 2026" pode terminar em 2025 e ainda deve aparecer durante 2026.
        filters.append("""
             AND TRY_CONVERT(
                 int,
                 SUBSTRING(
                     C.DESC_COLECAO,
                     NULLIF(PATINDEX('%20[0-9][0-9]%', C.DESC_COLECAO), 0),
                     4
                 )
             ) >= :anoInicialColecao
            """);
        params.addValue("anoInicialColecao", anoAtual);

        if (!griffeFilters.isEmpty()) {
            filters.append(" AND RTRIM(LTRIM(C.GRIFFE)) IN (:griffes)");
            params.addValue("griffes", griffeFilters);
        }
        if (searchFilter != null) {
            filters.append("""
                 AND (
                     UPPER(CONVERT(varchar(300), ISNULL(C.DESC_COLECAO, ''))) LIKE :search ESCAPE '\\'
                     OR UPPER(RTRIM(LTRIM(C.COLECAO))) LIKE :search ESCAPE '\\'
                 )
                """);
            params.addValue(
                "search",
                "%" + escapeLike(searchFilter.toUpperCase(Locale.ROOT)) + "%"
            );
        }

        String sql = """
            SELECT
                RTRIM(LTRIM(C.COLECAO)) AS CODIGO,
                RTRIM(LTRIM(C.DESC_COLECAO)) AS NOME,
                RTRIM(LTRIM(C.GRIFFE)) AS GRIFFE_PRODUTO,
                NULLIF(RTRIM(LTRIM(C.GRIFFE)), '') AS GRIFFE_COLECAO,
                C.DATA_INICIO_META,
                C.DATA_FINAL_META,
                RTRIM(LTRIM(CONVERT(varchar(50), C.TEMPORADA))) AS TEMPORADA,
                COUNT_BIG(*) AS QUANTIDADE_PRODUTOS
            """ + filters + """
             GROUP BY C.COLECAO, C.DESC_COLECAO, C.GRIFFE,
                      C.DATA_INICIO_META, C.DATA_FINAL_META, C.TEMPORADA
             ORDER BY C.GRIFFE, C.COLECAO
            """;

        return jdbc.query(sql, params, this::mapColecaoValida);
    }

    /**
     * Retorna as roupas ativas vinculadas diretamente à coleção informada no Linx.
     * A associação usa PRODUTOS.COLECAO = COLECOES.COLECAO e valida a griffe da coleção.
     */
    public List<LinxProdutoValidoResponseDTO> getProdutosDaColecao(
        String codigoColecao,
        String griffe
    ) {
        requireExpectedDatabase();
        String codigo = blankToNull(codigoColecao);
        if (codigo == null) {
            throw new BusinessRuleException("O código da coleção é obrigatório.");
        }

        List<String> griffes = normalizeGriffeFilters(singletonOrEmpty(griffe));
        if (griffes.isEmpty()) {
            throw new BusinessRuleException("A griffe da coleção é obrigatória.");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("codigoColecao", codigo.trim())
            .addValue("griffes", griffes);

        String sql = """
            SELECT
                P.ID,
                RTRIM(LTRIM(P.PRODUTO)) AS CODIGO,
                REPLACE(REPLACE(UPPER(RTRIM(LTRIM(P.PRODUTO))), '.', ''), ' ', '') AS CODIGO_NORMALIZADO,
                RTRIM(LTRIM(P.DESC_PRODUTO)) AS DESCRICAO,
                RTRIM(LTRIM(P.GRIFFE)) AS GRIFFE,
                RTRIM(LTRIM(C.COLECAO)) AS COLECAO_CODIGO,
                RTRIM(LTRIM(C.DESC_COLECAO)) AS COLECAO_NOME,
                NULLIF(RTRIM(LTRIM(C.GRIFFE)), '') AS GRIFFE_COLECAO,
                C.DATA_INICIO_META,
                C.DATA_FINAL_META,
                RTRIM(LTRIM(CONVERT(varchar(50), C.TEMPORADA))) AS TEMPORADA,
                RTRIM(LTRIM(P.GRUPO_PRODUTO)) AS GRUPO_PRODUTO,
                RTRIM(LTRIM(P.SUBGRUPO_PRODUTO)) AS SUBGRUPO_PRODUTO,
                RTRIM(LTRIM(P.TIPO_PRODUTO)) AS TIPO_PRODUTO,
                RTRIM(LTRIM(P.PERIODO_PCP)) AS PERIODO_PCP,
                RTRIM(LTRIM(P.GRADE)) AS GRADE,
                RTRIM(LTRIM(P.LINHA)) AS LINHA,
                RTRIM(LTRIM(P.CARTELA)) AS CARTELA,
                RTRIM(LTRIM(P.MATERIAL)) AS MATERIAL,
                (
                    SELECT MIN(RTRIM(LTRIM(M.DESC_MATERIAL)))
                    FROM dbo.MATERIAIS AS M
                    WHERE M.MATERIAL = P.MATERIAL
                ) AS DESC_MATERIAL,
                RTRIM(LTRIM(P.MODELAGEM)) AS MODELAGEM,
                RTRIM(LTRIM(P.ESTILISTA)) AS ESTILISTA,
                RTRIM(LTRIM(P.MODELISTA)) AS MODELISTA,
                RTRIM(LTRIM(CONVERT(varchar(30), P.STATUS_PRODUTO))) AS STATUS_PRODUTO,
                RTRIM(LTRIM(CONVERT(varchar(30), P.TIPO_STATUS_PRODUTO))) AS TIPO_STATUS_PRODUTO,
                P.DATA_CADASTRAMENTO,
                P.DATA_INICIO_DESENVOLVIMENTO,
                P.CUSTO_REPOSICAO1,
                P.PRECO_REPOSICAO_1
            """ + COLECOES_VALIDAS_FROM_WHERE + """
             AND RTRIM(LTRIM(C.COLECAO)) = :codigoColecao
             AND RTRIM(LTRIM(C.GRIFFE)) IN (:griffes)
             AND RTRIM(LTRIM(P.GRIFFE)) IN (:griffes)
             ORDER BY P.PRODUTO, P.ID
            """;

        return jdbc.query(sql, params, this::mapProdutoValido);
    }

    public List<String> getSubgrupos() {
        requireExpectedDatabase();
        return queryStrings("""
            SELECT DISTINCT RTRIM(LTRIM(SUBGRUPO_PRODUTO)) AS VALOR
            FROM dbo.PRODUTOS_SUBGRUPO
            WHERE GRUPO_PRODUTO = 'ROUPAS'
              AND ISNULL(INATIVO, 0) = 0
              AND NULLIF(RTRIM(LTRIM(SUBGRUPO_PRODUTO)), '') IS NOT NULL
            ORDER BY VALOR
            """);
    }

    public List<String> getLinhas() {
        requireExpectedDatabase();
        return queryStrings("""
            SELECT DISTINCT RTRIM(LTRIM(LINHA)) AS VALOR
            FROM dbo.PRODUTOS_LINHAS
            WHERE NULLIF(RTRIM(LTRIM(LINHA)), '') IS NOT NULL
            ORDER BY VALOR
            """);
    }

    public List<String> getGriffes() {
        requireExpectedDatabase();
        return List.of("K&J BLACK", "KING&JOE", "KING&JOE PLAY");
    }

    /** Consulta paginada por limite, com pesquisa por nome ou código do fornecedor. */
    public List<LinxFornecedorDTO> getFornecedores(String search) {
        requireExpectedDatabase();
        return jdbc.query("""
            SELECT DISTINCT TOP (50)
                RTRIM(LTRIM(FORNECEDOR)) AS FORNECEDOR,
                RTRIM(LTRIM(COD_FORNECEDOR)) AS CODIGO
            FROM dbo.FORNECEDORES
            WHERE NULLIF(RTRIM(LTRIM(FORNECEDOR)), '') IS NOT NULL
              AND INATIVO = 0
              AND EXISTS (SELECT 1 FROM dbo.CADASTRO_CLI_FOR CF
                  WHERE CF.NOME_CLIFOR = FORNECEDORES.FORNECEDOR AND CF.INATIVO = 0)
              AND (:search = '' OR CHARINDEX(:search, FORNECEDOR) > 0
                   OR CHARINDEX(:search, COD_FORNECEDOR) > 0)
            ORDER BY FORNECEDOR, CODIGO
            """, new MapSqlParameterSource("search", search == null ? "" : search.trim()),
            (rs, rowNum) -> new LinxFornecedorDTO(clean(rs.getString("FORNECEDOR")), clean(rs.getString("CODIGO"))));
    }

    /** Cadastro ativo e vínculos cor/fornecedor, sem escolher ou converter custos. */
    public LinxMaterialCadastroDTO getMaterialCadastro(String codigo) {
        String codigoNormalizado = codigo == null ? "" : codigo.trim();
        if (codigoNormalizado.isEmpty() || codigoNormalizado.length() > 50) {
            throw new BusinessRuleException("Informe um código de material Linx válido (até 50 caracteres).");
        }

        requireExpectedDatabase();
        var params = new MapSqlParameterSource("codigo", codigoNormalizado);
        List<LinxMaterialCadastroDTO> materiais = jdbc.query("""
            SELECT TOP (1)
                RTRIM(LTRIM(M.MATERIAL)) AS CODIGO,
                RTRIM(LTRIM(M.DESC_MATERIAL)) AS NOME,
                RTRIM(LTRIM(M.GRUPO)) AS GRUPO,
                RTRIM(LTRIM(M.UNID_ESTOQUE)) AS UNIDADE_ESTOQUE,
                RTRIM(LTRIM(M.UNID_FICHA_TEC)) AS UNIDADE_FICHA_TECNICA,
                M.LARGURA AS LARGURA,
                M.COMPRIMENTO AS COMPRIMENTO,
                M.U_ALTURA AS ALTURA,
                M.U_RENDIMENTO AS RENDIMENTO,
                M.CUSTO_A_VISTA,
                M.CUSTO_REPOSICAO
            FROM dbo.MATERIAIS AS M
            WHERE RTRIM(LTRIM(M.MATERIAL)) = :codigo
              AND M.INATIVO = 0
            ORDER BY M.MATERIAL
            """, params, (rs, rowNum) -> new LinxMaterialCadastroDTO(
                clean(rs.getString("CODIGO")),
                clean(rs.getString("NOME")),
                clean(rs.getString("GRUPO")),
                clean(rs.getString("UNIDADE_ESTOQUE")),
                clean(rs.getString("UNIDADE_FICHA_TECNICA")),
                clean(rs.getString("LARGURA")),
                clean(rs.getString("COMPRIMENTO")),
                clean(rs.getString("ALTURA")),
                rs.getBigDecimal("RENDIMENTO"),
                List.of(),
                rs.getBigDecimal("CUSTO_A_VISTA"),
                rs.getBigDecimal("CUSTO_REPOSICAO"),
                List.of()
            ));
        if (materiais.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Nenhum material ativo do Linx foi encontrado para o código '" + codigoNormalizado + "'.");
        }

        // Gramatura e encolhimentos vivem no cadastro por cor; retornamos as opções
        // sem escolher uma delas implicitamente para o usuário.
        List<java.util.Map<String, Object>> cores = jdbc.queryForList("""
            SELECT
                RTRIM(LTRIM(C.MATERIAL)) AS MATERIAL,
                RTRIM(LTRIM(C.COR_MATERIAL)) AS CODIGO_COR,
                RTRIM(LTRIM(C.DESC_COR_MATERIAL)) AS NOME_COR,
                C.GRAMATURA,
                C.TEC_ENCOLHIMENTO_CORTE_X,
                C.TEC_ENCOLHIMENTO_CORTE_Y,
                C.TEC_ENCOLHIMENTO_ACABAMENTO_X,
                C.TEC_ENCOLHIMENTO_ACABAMENTO_Y,
                C.TEC_ENCOLHIMENTO_LAVAGEM_X,
                C.TEC_ENCOLHIMENTO_LAVAGEM_Y,
                C.CUSTO_A_VISTA,
                C.CUSTO_REPOSICAO
            FROM dbo.MATERIAIS_CORES AS C
            WHERE RTRIM(LTRIM(C.MATERIAL)) = :codigo
              AND C.INATIVO = 0
            ORDER BY C.COR_MATERIAL
            """, params);

        // MATERIAIS_FORNECEDOR não possui INATIVO: a validade vem da cor,
        // de FORNECEDORES e do cadastro correspondente em CADASTRO_CLI_FOR.
        List<java.util.Map<String, Object>> fornecedores = jdbc.queryForList("""
            SELECT
                RTRIM(LTRIM(MF.COR_MATERIAL)) AS CODIGO_COR,
                RTRIM(LTRIM(C.DESC_COR_MATERIAL)) AS NOME_COR,
                RTRIM(LTRIM(MF.FORNECEDOR)) AS FORNECEDOR,
                RTRIM(LTRIM(F.COD_FORNECEDOR)) AS CODIGO_FORNECEDOR,
                RTRIM(LTRIM(MF.UNIDADE)) AS UNIDADE,
                MF.FATOR_CONVERSAO_FORNECEDOR,
                MF.CUSTO_REPOSICAO,
                'MATERIAIS_FORNECEDOR.CUSTO_REPOSICAO' AS FONTE
            FROM dbo.MATERIAIS_FORNECEDOR MF
            INNER JOIN dbo.MATERIAIS_CORES C
                ON C.MATERIAL = MF.MATERIAL AND C.COR_MATERIAL = MF.COR_MATERIAL
            INNER JOIN dbo.FORNECEDORES F ON F.FORNECEDOR = MF.FORNECEDOR
            INNER JOIN dbo.CADASTRO_CLI_FOR CF ON CF.NOME_CLIFOR = F.FORNECEDOR
            WHERE MF.MATERIAL = :codigo
              AND C.INATIVO = 0 AND F.INATIVO = 0 AND CF.INATIVO = 0
            ORDER BY MF.COR_MATERIAL, F.FORNECEDOR, F.COD_FORNECEDOR
            """, params);

        LinxMaterialCadastroDTO base = materiais.getFirst();
        return new LinxMaterialCadastroDTO(base.codigo(), base.nome(), base.grupo(),
            base.unidadeEstoque(), base.unidadeFichaTecnica(), base.largura(), base.comprimento(),
            base.altura(), base.rendimento(), cores, base.custoAVista(), base.custoReposicao(), fornecedores);
    }

    public List<String> getCoresBasicas(String search) {
        requireExpectedDatabase();
        return jdbc.query("""
            SELECT DISTINCT TOP (50) RTRIM(LTRIM(DESC_COR)) AS VALOR
            FROM dbo.CORES_BASICAS
            WHERE NULLIF(RTRIM(LTRIM(DESC_COR)), '') IS NOT NULL
              AND (:search = '' OR CHARINDEX(:search, DESC_COR) > 0)
            ORDER BY VALOR
            """, new MapSqlParameterSource("search", search == null ? "" : search.trim()),
            (rs, rowNum) -> clean(rs.getString("VALOR")));
    }

    /** Lista todas as descrições de cores, sem o limite usado no autocomplete. */
    public List<String> getCoresCatalogo() {
        requireExpectedDatabase();
        return queryStrings("""
            SELECT DISTINCT RTRIM(LTRIM(DESC_COR)) AS VALOR
            FROM dbo.CORES_BASICAS
            WHERE NULLIF(RTRIM(LTRIM(DESC_COR)), '') IS NOT NULL
            ORDER BY VALOR
            """);
    }

    public List<String> getModelagens() {
        requireExpectedDatabase();
        return queryStrings("""
            SELECT DISTINCT RTRIM(LTRIM(PRODUTO)) AS VALOR
            FROM dbo.PRODUTOS
            WHERE NULLIF(RTRIM(LTRIM(PRODUTO)), '') IS NOT NULL
            ORDER BY VALOR
            """);
    }

    /** Cores disponíveis para o mostruário, lidas diretamente do cadastro Linx. */
    public List<String> getCoresMostruario() {
        return getCoresCatalogo();
    }

    /** Mesmo catálogo completo, mantendo a identidade da cor; não deduz COR_PRODUTO. */
    public List<LinxCorCodigoDTO> getCoresMostruarioComCodigo() {
        requireExpectedDatabase();
        return jdbc.query("""
            SELECT COR, DESC_COR FROM dbo.CORES_BASICAS
            WHERE NULLIF(LTRIM(RTRIM(COR)), '') IS NOT NULL
              AND NULLIF(LTRIM(RTRIM(DESC_COR)), '') IS NOT NULL
            ORDER BY DESC_COR, COR
            """, new MapSqlParameterSource(),
            (rs, rowNum) -> new LinxCorCodigoDTO(clean(rs.getString("COR")), clean(rs.getString("DESC_COR"))));
    }

    public List<String> getGrades() {
        requireExpectedDatabase();
        return queryStrings("""
            SELECT DISTINCT RTRIM(LTRIM(GRADE)) AS VALOR
            FROM dbo.PRODUTOS_TAMANHOS
            WHERE NULLIF(RTRIM(LTRIM(GRADE)), '') IS NOT NULL
            ORDER BY VALOR
            """);
    }

    /** Consulta exata, parametrizada. NUMERO_TAMANHOS não gera posições ausentes. */
    public LinxGradeDetalheDTO getGradeDetalhe(String grade) {
        String codigo = grade == null ? "" : grade.trim();
        if (codigo.isEmpty() || codigo.length() > 25)
            throw new BusinessRuleException("Informe uma grade Linx válida, com até 25 caracteres.");
        requireExpectedDatabase();
        String campos = IntStream.rangeClosed(1, 48).mapToObj(i -> "TAMANHO_" + i)
            .collect(Collectors.joining(", "));
        List<LinxGradeDetalheDTO> grades = jdbc.query(
            "SELECT GRADE, NUMERO_TAMANHOS, " + campos + " FROM dbo.PRODUTOS_TAMANHOS WHERE GRADE = :grade",
            new MapSqlParameterSource("grade", codigo), (rs, rowNum) -> {
                List<LinxGradeDetalheDTO.Posicao> posicoes = new ArrayList<>();
                for (int i = 1; i <= 48; i++) {
                    String tamanho = clean(rs.getString("TAMANHO_" + i));
                    posicoes.add(new LinxGradeDetalheDTO.Posicao(i, tamanho));
                }
                return new LinxGradeDetalheDTO(clean(rs.getString("GRADE")), rs.getInt("NUMERO_TAMANHOS"), List.copyOf(posicoes));
            });
        if (grades.size() != 1)
            throw new BusinessRuleException("Grade não encontrada ou ambígua no Linx. Nenhum tamanho foi deduzido.");
        return grades.getFirst();
    }

    /** Busca por nome ou código; preserva cores com a mesma descrição e códigos distintos. */
    public List<LinxCorCodigoDTO> getCoresComCodigo(String search) {
        String termo = search == null ? "" : search.trim();
        if (termo.length() > 100) throw new BusinessRuleException("A busca de cor aceita até 100 caracteres.");
        requireExpectedDatabase();
        return jdbc.query("""
            SELECT TOP (50) COR, DESC_COR FROM dbo.CORES_BASICAS
            WHERE USO_PRODUTOS = 1 AND NULLIF(LTRIM(RTRIM(COR)), '') IS NOT NULL
              AND (:search = '' OR CHARINDEX(:search, COR) > 0 OR CHARINDEX(:search, DESC_COR) > 0)
            ORDER BY DESC_COR, COR
            """, new MapSqlParameterSource("search", termo),
            (rs, rowNum) -> new LinxCorCodigoDTO(clean(rs.getString("COR")), clean(rs.getString("DESC_COR"))));
    }

    public List<String> getCategorias() {
        requireExpectedDatabase();
        return queryStrings("""
            SELECT DISTINCT RTRIM(LTRIM(CATEGORIA_PRODUTO)) AS VALOR
            FROM dbo.PRODUTOS_CATEGORIA
            WHERE NULLIF(RTRIM(LTRIM(CATEGORIA_PRODUTO)), '') IS NOT NULL
            ORDER BY VALOR
            """);
    }

    public LinxOpcoesResponseDTO getOpcoesLinx() {
        requireExpectedDatabase();
        return new LinxOpcoesResponseDTO(
            getSubgrupos(), getLinhas(), getGriffes(), getGrades(), getCategorias()
        );
    }

    LinxConnectionInfoDTO requireExpectedDatabase() {
        LinxConnectionInfoDTO connection = getConnectionInfo();
        if (!connection.bancoEsperadoConfirmado()) {
            throw new BusinessRuleException(
                "Integração Linx bloqueada: a conexão atual aponta para o banco '" +
                    connection.banco() + "', mas deve apontar para '" + connection.bancoEsperado() + "'."
            );
        }
        if (!connection.credencialSomenteLeitura()
            && !databaseClient.allowWriteCapableCredential()) {
            throw new BusinessRuleException(
                "Integração Linx bloqueada: use uma credencial SQL exclusiva com somente SELECT. " +
                    "Permissões de escrita detectadas: " + connection.permissoesEscritaDetectadas()
            );
        }
        return connection;
    }

    private LinxProdutoValidoResponseDTO mapProdutoValido(ResultSet rs, int rowNum) throws SQLException {
        return new LinxProdutoValidoResponseDTO(
            nullableLong(rs, "ID"),
            clean(rs.getString("CODIGO")),
            clean(rs.getString("CODIGO_NORMALIZADO")),
            clean(rs.getString("DESCRICAO")),
            clean(rs.getString("GRIFFE")),
            clean(rs.getString("COLECAO_CODIGO")),
            clean(rs.getString("COLECAO_NOME")),
            clean(rs.getString("GRIFFE_COLECAO")),
            toLocalDateTime(rs, "DATA_INICIO_META"),
            toLocalDateTime(rs, "DATA_FINAL_META"),
            clean(rs.getString("TEMPORADA")),
            clean(rs.getString("GRUPO_PRODUTO")),
            clean(rs.getString("SUBGRUPO_PRODUTO")),
            clean(rs.getString("TIPO_PRODUTO")),
            clean(rs.getString("PERIODO_PCP")),
            clean(rs.getString("GRADE")),
            clean(rs.getString("LINHA")),
            clean(rs.getString("CARTELA")),
            clean(rs.getString("MATERIAL")),
            clean(rs.getString("DESC_MATERIAL")),
            clean(rs.getString("MODELAGEM")),
            clean(rs.getString("ESTILISTA")),
            clean(rs.getString("MODELISTA")),
            clean(rs.getString("STATUS_PRODUTO")),
            clean(rs.getString("TIPO_STATUS_PRODUTO")),
            toLocalDateTime(rs, "DATA_CADASTRAMENTO"),
            toLocalDateTime(rs, "DATA_INICIO_DESENVOLVIMENTO"),
            rs.getBigDecimal("CUSTO_REPOSICAO1"),
            rs.getBigDecimal("PRECO_REPOSICAO_1")
        );
    }

    private LinxColecaoValidaResponseDTO mapColecaoValida(ResultSet rs, int rowNum) throws SQLException {
        return new LinxColecaoValidaResponseDTO(
            clean(rs.getString("CODIGO")),
            clean(rs.getString("NOME")),
            clean(rs.getString("GRIFFE_PRODUTO")),
            clean(rs.getString("GRIFFE_COLECAO")),
            toLocalDateTime(rs, "DATA_INICIO_META"),
            toLocalDateTime(rs, "DATA_FINAL_META"),
            clean(rs.getString("TEMPORADA")),
            rs.getLong("QUANTIDADE_PRODUTOS")
        );
    }

    private List<String> queryStrings(String sql) {
        return jdbc.query(
            sql,
            new MapSqlParameterSource(),
            (rs, rowNum) -> clean(rs.getString("VALOR"))
        );
    }

    private Set<String> findWorkflowProductCodes(
        List<EtapaPecaEnum> etapas,
        List<LinxStatusPecaFiltro> statusPecas,
        List<StatusColecaoEnum> statusColecoes
    ) {
        Specification<Peca> specification = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!safeList(etapas).isEmpty()) {
                predicates.add(root.get("etapaAtual").in(safeList(etapas)));
            }
            if (!safeList(statusPecas).isEmpty()) {
                Set<StatusPecaEnum> statuses = new HashSet<>();
                for (LinxStatusPecaFiltro statusPeca : safeList(statusPecas)) {
                    statuses.addAll(statusPeca.statuses());
                }
                predicates.add(root.get("status").in(statuses));
            }
            if (!safeList(statusColecoes).isEmpty()) {
                predicates.add(root.get("colecao").get("status").in(safeList(statusColecoes)));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };

        Set<String> codes = new HashSet<>();
        for (Peca peca : pecaRepository.findAll(specification)) {
            String normalized = normalizeProductCode(peca.getCodigo());
            if (normalized != null) {
                codes.add(normalized);
            }
        }
        return codes;
    }

    private LinxProdutosValidosPageResponseDTO emptyPage(
        int page,
        int size,
        String sourceDatabase
    ) {
        return emptyPage(page, size, sourceDatabase, 0);
    }

    private LinxProdutosValidosPageResponseDTO emptyPage(
        int page,
        int size,
        String sourceDatabase,
        long totalElements
    ) {
        return new LinxProdutosValidosPageResponseDTO(
            List.of(),
            page,
            size,
            totalElements,
            calculateTotalPages(totalElements, size),
            false,
            sourceDatabase,
            true
        );
    }

    private String normalizeProductCode(String value) {
        String cleanValue = clean(value);
        return cleanValue == null
            ? null
            : cleanValue.toUpperCase(Locale.ROOT).replace(".", "").replaceAll("\\s+", "");
    }

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        long pages = (totalElements + size - 1) / size;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    private List<String> normalizeGriffeFilters(List<String> griffes) {
        return normalizeTextFilters(griffes).stream()
            .map(value -> switch (value.toUpperCase(Locale.ROOT).replaceAll("\\s+", "")) {
                case "K&JBLACK" -> "K&J BLACK";
                case "KING&JOE" -> "KING&JOE";
                case "KING&JOEPLAY" -> "KING&JOE PLAY";
                default -> throw new BusinessRuleException(
                    "Griffe inválida. Use K&J BLACK, KING&JOE ou KING&JOE PLAY."
                );
            })
            .distinct()
            .toList();
    }

    private List<String> normalizeTextFilters(List<String> values) {
        return safeList(values).stream()
            .map(this::blankToNull)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }

    private <T> List<T> singletonOrEmpty(T value) {
        return value == null ? List.of() : List.of(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private java.time.LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record ConnectionIdentity(String server, String database) {
    }

    private record LinxProductKey(Long id, String normalizedCode) {
    }
}
