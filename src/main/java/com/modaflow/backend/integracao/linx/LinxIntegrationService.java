package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

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
        WHERE NULLIF(RTRIM(LTRIM(P.PRODUTO)), '') IS NOT NULL
          AND NULLIF(RTRIM(LTRIM(P.COLECAO)), '') IS NOT NULL
          AND ISNULL(P.INATIVO, 0) = 0
          AND ISNULL(C.INATIVO, 0) = 0
          AND P.GRIFFE IN ('K&J BLACK', 'KING&JOE', 'KING&JOE PLAY')
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

    public LinxIntegrationService(LinxDatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        this.jdbc = databaseClient.jdbc();
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
        LinxConnectionInfoDTO connection = requireExpectedDatabase();
        String griffeFilter = normalizeGriffeFilter(griffe);
        String searchFilter = blankToNull(search);
        String colecaoFilter = blankToNull(colecao);
        String tipoFilter = blankToNull(tipo);
        String materialFilter = blankToNull(material);

        StringBuilder filters = new StringBuilder(PRODUTOS_VALIDOS_FROM_WHERE);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (griffeFilter != null) {
            filters.append(" AND P.GRIFFE = :griffe");
            params.addValue("griffe", griffeFilter);
        }
        if (colecaoFilter != null) {
            filters.append(" AND P.COLECAO = :colecao");
            params.addValue("colecao", colecaoFilter);
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
        if (tipoFilter != null) {
            filters.append(" AND UPPER(RTRIM(LTRIM(P.SUBGRUPO_PRODUTO))) = :tipo");
            params.addValue("tipo", tipoFilter.toUpperCase(Locale.ROOT));
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

        Long count = jdbc.queryForObject("SELECT COUNT_BIG(*) " + filters, params, Long.class);
        long totalElements = count == null ? 0 : count;
        long offset = (long) page * size;
        params.addValue("offset", offset);
        params.addValue("size", size);

        String dataSql = """
            SELECT
                P.ID,
                RTRIM(LTRIM(P.PRODUTO)) AS CODIGO,
                REPLACE(REPLACE(UPPER(RTRIM(LTRIM(P.PRODUTO))), '.', ''), ' ', '') AS CODIGO_NORMALIZADO,
                RTRIM(LTRIM(P.DESC_PRODUTO)) AS DESCRICAO,
                RTRIM(LTRIM(P.GRIFFE)) AS GRIFFE,
                RTRIM(LTRIM(P.COLECAO)) AS COLECAO_CODIGO,
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
            """ + filters + """
             ORDER BY P.GRIFFE, P.COLECAO, P.PRODUTO, P.ID
             OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

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
    public List<LinxColecaoValidaResponseDTO> getColecoesValidas(String griffe, String search) {
        requireExpectedDatabase();
        String griffeFilter = normalizeGriffeFilter(griffe);
        String searchFilter = blankToNull(search);
        StringBuilder filters = new StringBuilder(PRODUTOS_VALIDOS_FROM_WHERE);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (griffeFilter != null) {
            filters.append(" AND P.GRIFFE = :griffe");
            params.addValue("griffe", griffeFilter);
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
                RTRIM(LTRIM(P.GRIFFE)) AS GRIFFE_PRODUTO,
                NULLIF(RTRIM(LTRIM(C.GRIFFE)), '') AS GRIFFE_COLECAO,
                C.DATA_INICIO_META,
                C.DATA_FINAL_META,
                RTRIM(LTRIM(CONVERT(varchar(50), C.TEMPORADA))) AS TEMPORADA,
                COUNT_BIG(*) AS QUANTIDADE_PRODUTOS
            """ + filters + """
             GROUP BY C.COLECAO, C.DESC_COLECAO, P.GRIFFE, C.GRIFFE,
                      C.DATA_INICIO_META, C.DATA_FINAL_META, C.TEMPORADA
             ORDER BY P.GRIFFE, C.COLECAO
            """;

        return jdbc.query(sql, params, this::mapColecaoValida);
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

    public List<String> getGrades() {
        requireExpectedDatabase();
        return queryStrings("""
            SELECT DISTINCT RTRIM(LTRIM(GRADE)) AS VALOR
            FROM dbo.PRODUTOS_TAMANHOS
            WHERE NULLIF(RTRIM(LTRIM(GRADE)), '') IS NOT NULL
            ORDER BY VALOR
            """);
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

    private LinxConnectionInfoDTO requireExpectedDatabase() {
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

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        long pages = (totalElements + size - 1) / size;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    private String normalizeGriffeFilter(String griffe) {
        String value = blankToNull(griffe);
        if (value == null) {
            return null;
        }
        return switch (value.toUpperCase(Locale.ROOT).replaceAll("\\s+", "")) {
            case "K&JBLACK" -> "K&J BLACK";
            case "KING&JOE" -> "KING&JOE";
            case "KING&JOEPLAY" -> "KING&JOE PLAY";
            default -> throw new BusinessRuleException(
                "Griffe inválida. Use K&J BLACK, KING&JOE ou KING&JOE PLAY."
            );
        };
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
}
