package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@ConditionalOnProperty(prefix = "modaflow.linx", name = "enabled", havingValue = "true")
public class LinxFichaService {
    private static final Set<String> GRIFFES = Set.of("K&J BLACK", "KING&JOE", "KING&JOE PLAY");
    private static final String HEADERS = """
        SELECT %s H.PRODUTO, H.VERSAO_FICHA, H.DESC_VERSAO_FICHA, H.DATA_CRIACAO,
               H.RESPONSAVEL, H.STATUS_VERSAO_FICHA, H.U_COLECAO, C.DESC_COLECAO
        FROM dbo.PRODUTO_FICHA_VERSAO H
        LEFT JOIN dbo.COLECOES C ON C.COLECAO = H.U_COLECAO
        WHERE H.PRODUTO = :produto
          AND EXISTS (SELECT 1 FROM dbo.PRODUTOS P WHERE P.PRODUTO = H.PRODUTO
                      AND P.GRIFFE = :griffe AND P.GRUPO_PRODUTO = 'ROUPAS')
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final LinxIntegrationService integration;

    public LinxFichaService(LinxDatabaseClient client, LinxIntegrationService integration) {
        this.jdbc = client.jdbc();
        this.integration = integration;
    }

    private static final String COLLECTION_SCOPE = " AND H.U_COLECAO = :colecao AND C.GRIFFE = :griffe AND RTRIM(LTRIM(C.DESC_COLECAO)) = :descColecao ";

    /** Apenas vínculos do produto atualmente pertencente à coleção/griffe informadas. */
    public List<LinxCorProdutoDTO> getCoresProduto(String produto, String griffe, String colecao, String descColecao) {
        var params = collectionParams(produto, griffe, colecao, descColecao);
        integration.requireExpectedDatabase();
        return jdbc.query("""
            SELECT PC.PRODUTO, PC.COR_PRODUTO, PC.DESC_COR_PRODUTO, PC.COR,
                   CB.DESC_COR, PC.U_COLECAO, ISNULL(PC.U_INATIVO, 0) AS INATIVA
            FROM dbo.PRODUTO_CORES PC
            INNER JOIN dbo.PRODUTOS P ON P.PRODUTO = PC.PRODUTO
            INNER JOIN dbo.COLECOES C ON C.COLECAO = P.COLECAO
            LEFT JOIN dbo.CORES_BASICAS CB ON CB.COR = PC.COR
            WHERE P.PRODUTO = :produto AND P.GRIFFE = :griffe AND P.GRUPO_PRODUTO = 'ROUPAS'
              AND P.COLECAO = :colecao AND C.GRIFFE = :griffe
              AND RTRIM(LTRIM(C.DESC_COLECAO)) = :descColecao
              AND (PC.U_COLECAO = :colecao OR NULLIF(LTRIM(RTRIM(PC.U_COLECAO)), '') IS NULL)
            ORDER BY PC.DESC_COR_PRODUTO, PC.COR_PRODUTO
            """, params, (rs, rowNum) -> new LinxCorProdutoDTO(
                text(rs.getString("PRODUTO")), text(rs.getString("COR_PRODUTO")), text(rs.getString("DESC_COR_PRODUTO")),
                text(rs.getString("COR")), text(rs.getString("DESC_COR")), text(rs.getString("U_COLECAO")), rs.getBoolean("INATIVA")));
    }

    /** Histórico estritamente vinculado à coleção, nunca a todas as fichas do código. */
    public List<LinxFichaVersaoDTO> getVersoes(String produto, String griffe, String colecao, String descColecao) {
        integration.requireExpectedDatabase();
        return jdbc.query(HEADERS.formatted("") + COLLECTION_SCOPE +
            " ORDER BY H.DATA_CRIACAO DESC, H.VERSAO_FICHA DESC", collectionParams(produto, griffe, colecao, descColecao), this::mapHeader);
    }

    public LinxFichaDetalheDTO getDetalhe(String produto, String versao, String griffe, String colecao, String descColecao) {
        integration.requireExpectedDatabase();
        var params = collectionParams(produto, griffe, colecao, descColecao).addValue("versao", required(versao, 5, "Versão"));
        var headers = jdbc.query(HEADERS.formatted("") + COLLECTION_SCOPE + " AND H.VERSAO_FICHA = :versao", params, this::mapHeader);
        if (headers.isEmpty()) throw new ResourceNotFoundException("Ficha não encontrada para o produto, versão, griffe e coleção informados.");
        return getMateriais(headers.getFirst(), params);
    }

    /** Base antiga: somente uma ficha atual, sem carregar a lista de versões anteriores. */
    public LinxFichaDetalheDTO getAtual(String produto, String griffe, String colecao, String descColecao) {
        integration.requireExpectedDatabase();
        var params = collectionParams(produto, griffe, colecao, descColecao);
        var headers = jdbc.query(HEADERS.formatted("TOP (1)") + """
            AND (H.U_COLECAO = :colecao OR NULLIF(LTRIM(RTRIM(H.U_COLECAO)), '') IS NULL)
            AND EXISTS (SELECT 1 FROM dbo.PRODUTOS P INNER JOIN dbo.COLECOES PC ON PC.COLECAO = P.COLECAO
                        WHERE P.PRODUTO = H.PRODUTO AND P.VERSAO_FICHA = H.VERSAO_FICHA AND P.COLECAO = :colecao
                          AND P.GRIFFE = :griffe AND PC.GRIFFE = :griffe
                          AND RTRIM(LTRIM(PC.DESC_COLECAO)) = :descColecao)
            ORDER BY H.DATA_CRIACAO DESC, H.VERSAO_FICHA DESC
            """, params, this::mapHeader);
        if (headers.isEmpty()) throw new ResourceNotFoundException("Nenhuma ficha atual encontrada para a peça desta coleção.");
        var header = headers.getFirst();
        params.addValue("versao", header.versao());
        return getMateriais(header, params);
    }

    private LinxFichaDetalheDTO getMateriais(LinxFichaVersaoDTO header, MapSqlParameterSource params) {

        String consumos = IntStream.rangeClosed(1, 48).mapToObj(i -> "V.C" + i).collect(Collectors.joining(", "));
        String tamanhos = IntStream.rangeClosed(1, 48).mapToObj(i -> "T.TAMANHO_" + i).collect(Collectors.joining(", "));
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT V.PRODUTO, V.VERSAO_FICHA, DB_NAME() AS BANCO_ORIGEM, P.GRADE,
                   V.MATERIAL, V.ITEM, M.DESC_MATERIAL, M.GRUPO, M.TIPO, M.SUBGRUPO,
                   M.COD_MATERIAL_TAMANHO, M.VARIA_MATERIAL_TAMANHO, M.FABRICANTE, M.REF_FABRICANTE,
                   M.COMPRIMENTO, M.LARGURA AS LARGURA_MATERIAL, M.UNID_ESTOQUE, M.UNID_FICHA_TEC,
                   M.FATOR_CONVERSAO, M.CUSTO_REPOSICAO, M.CUSTO_A_VISTA, M.OBS AS OBS_MATERIAL,
                   M.SEMI_ACABADO, M.VARIA_CUSTO_COR, M.VARIA_PRECO_COR, M.U_ALTURA,
                   M.U_RENDIMENTO, MC.DESC_COMPOSICAO, M.DESC_COMPOSICAO AS DESC_COMPOSICAO_MATERIAL,
                   CONVERT(varchar(23), M.DATA_REPOSICAO, 121) AS DATA_REPOSICAO,
                   V.FASE_PRODUCAO, F.DESC_FASE_PRODUCAO, V.SETOR_PRODUCAO, S.DESC_SETOR_PRODUCAO,
                   V.DESC_USO_MATERIAL, V.PARTE_APLICADO_MATERIAL, V.CONSUMO_P_TAMANHO,
                   V.MATERIAL_PRINCIPAL, V.MATERIAL_DEPENDENTE, V.INDICA_PACOTE_MATERIAIS,
                   V.PACOTE_MATERIAIS, PM.DESC_PACOTE_MATERIAIS, V.LARGURA, V.ALTURA, V.TIPO_PP,
                   V.CONSUMO_FIXO,
            """ + tamanhos + ", " + consumos + " " + """
            FROM dbo.PRODUTO_VERSAO_MATERIAL V
            INNER JOIN dbo.PRODUTOS P ON P.PRODUTO = V.PRODUTO
            LEFT JOIN dbo.PRODUTOS_TAMANHOS T ON T.GRADE = P.GRADE
            INNER JOIN dbo.MATERIAIS M ON M.MATERIAL = V.MATERIAL
            LEFT JOIN dbo.MATERIAIS_COMPOSICAO MC ON MC.COMPOSICAO = M.COMPOSICAO
            LEFT JOIN dbo.PRODUCAO_SETOR S ON S.SETOR_PRODUCAO = V.SETOR_PRODUCAO AND S.FASE_PRODUCAO = V.FASE_PRODUCAO
            LEFT JOIN dbo.PRODUCAO_FASE F ON F.FASE_PRODUCAO = V.FASE_PRODUCAO
            LEFT JOIN dbo.PACOTE_MATERIAIS PM ON PM.PACOTE_MATERIAIS = V.PACOTE_MATERIAIS
            WHERE V.PRODUTO = :produto AND V.VERSAO_FICHA = :versao
            ORDER BY V.ITEM, V.MATERIAL
            """, params);
        List<Map<String, Object>> colorRows = jdbc.queryForList("""
            SELECT VC.MATERIAL, VC.ITEM, VC.COR_PRODUTO, VC.COR_MATERIAL, VC.PORCENTAGEM_CONSUMO,
                   C.CUSTO_A_VISTA, C.CUSTO_REPOSICAO, C.COMPOSICAO,
                   C.DESC_COR_MATERIAL, C.GRAMATURA, C.TEC_ENCOLHIMENTO_CORTE_X, C.TEC_ENCOLHIMENTO_CORTE_Y,
                   C.TEC_ENCOLHIMENTO_ACABAMENTO_X, C.TEC_ENCOLHIMENTO_ACABAMENTO_Y,
                   C.TEC_ENCOLHIMENTO_LAVAGEM_X, C.TEC_ENCOLHIMENTO_LAVAGEM_Y
            FROM dbo.PRODUTO_VERSAO_MATERIAL_COR VC
            INNER JOIN dbo.MATERIAIS M ON M.MATERIAL = VC.MATERIAL
            LEFT JOIN dbo.MATERIAIS_CORES C ON C.MATERIAL = VC.MATERIAL AND C.COR_MATERIAL = VC.COR_MATERIAL
            WHERE VC.PRODUTO = :produto AND VC.VERSAO_FICHA = :versao
            ORDER BY VC.ITEM, VC.MATERIAL, VC.COR_PRODUTO, VC.COR_MATERIAL
            """, params);
        Map<List<String>, List<Map<String, Object>>> colors = new LinkedHashMap<>();
        colorRows.forEach(row -> colors.computeIfAbsent(key(row), ignored -> new ArrayList<>()).add(cleanRow(row)));
        var materials = rows.stream().map(row -> new LinxFichaMaterialDTO(
            text(row.get("MATERIAL")), text(row.get("DESC_MATERIAL")), text(row.get("GRUPO")), text(row.get("ITEM")),
            cleanRow(row), colors.getOrDefault(key(row), List.of())
        )).toList();
        return new LinxFichaDetalheDTO(header, materials);
    }

    public List<LinxFichaResumoDTO> getResumosDaColecao(String colecao, String griffe, String descColecao, boolean legado) {
        integration.requireExpectedDatabase();
        var params = new MapSqlParameterSource("colecao", required(colecao, 6, "Coleção"))
            .addValue("griffe", griffe(griffe)).addValue("descColecao", required(descColecao, 240, "Descrição da coleção"))
            .addValue("legado", legado ? 1 : 0);
        var rows = jdbc.queryForList("""
            SELECT H.PRODUTO, H.VERSAO_FICHA, M.GRUPO, M.MATERIAL, M.DESC_MATERIAL
            FROM dbo.PRODUTOS P
            INNER JOIN dbo.COLECOES C ON C.COLECAO = P.COLECAO
            CROSS APPLY (
                SELECT TOP (1) V.PRODUTO, V.VERSAO_FICHA FROM dbo.PRODUTO_FICHA_VERSAO V
                WHERE V.PRODUTO = P.PRODUTO AND (V.U_COLECAO = C.COLECAO
                    OR (:legado = 1 AND NULLIF(LTRIM(RTRIM(V.U_COLECAO)), '') IS NULL))
                ORDER BY V.DATA_CRIACAO DESC, V.VERSAO_FICHA DESC
            ) H
            LEFT JOIN dbo.PRODUTO_VERSAO_MATERIAL VM ON VM.PRODUTO = H.PRODUTO AND VM.VERSAO_FICHA = H.VERSAO_FICHA
            LEFT JOIN dbo.MATERIAIS M ON M.MATERIAL = VM.MATERIAL
            WHERE C.COLECAO = :colecao AND C.GRIFFE = :griffe AND P.GRIFFE = :griffe
              AND RTRIM(LTRIM(C.DESC_COLECAO)) = :descColecao
              AND P.GRUPO_PRODUTO = 'ROUPAS' AND ISNULL(P.INATIVO, 0) = 0 AND ISNULL(C.INATIVO, 0) = 0
            ORDER BY H.PRODUTO, M.MATERIAL
            """, params);
        Map<String, LinxFichaResumoDTO> summaries = new LinkedHashMap<>();
        rows.forEach(row -> {
            String product = text(row.get("PRODUTO"));
            var summary = summaries.computeIfAbsent(product, ignored -> new LinxFichaResumoDTO(
                product, text(row.get("VERSAO_FICHA")), new ArrayList<>(), new ArrayList<>()));
            String group = text(row.get("GRUPO"));
            if (group == null) return;
            String material = text(row.get("MATERIAL"));
            String name = text(row.get("DESC_MATERIAL"));
            String label = name == null ? material : name + " (" + material + ")";
            var target = "TECIDO".equals(group) ? summary.tecidos() : summary.aviamentos();
            if (label != null && !target.contains(label)) target.add(label);
        });
        return List.copyOf(summaries.values());
    }

    private MapSqlParameterSource params(String produto, String griffe) {
        return new MapSqlParameterSource("produto", required(produto, 12, "Produto")).addValue("griffe", griffe(griffe));
    }
    private MapSqlParameterSource collectionParams(String produto, String griffe, String colecao, String descColecao) {
        return params(produto, griffe).addValue("colecao", required(colecao, 6, "Coleção"))
            .addValue("descColecao", required(descColecao, 240, "Descrição da coleção"));
    }
    private String griffe(String value) {
        String result = required(value, 40, "Griffe");
        if (!GRIFFES.contains(result)) throw new BusinessRuleException("Griffe inválida.");
        return result;
    }
    private String required(String value, int max, String field) {
        String result = text(value);
        if (result == null || result.length() > max) throw new BusinessRuleException(field + " obrigatório ou inválido.");
        return result;
    }
    private LinxFichaVersaoDTO mapHeader(ResultSet row, int index) throws SQLException {
        var date = row.getTimestamp("DATA_CRIACAO");
        return new LinxFichaVersaoDTO(text(row.getString("PRODUTO")), text(row.getString("VERSAO_FICHA")),
            text(row.getString("DESC_VERSAO_FICHA")), date == null ? null : date.toLocalDateTime(),
            text(row.getString("RESPONSAVEL")), text(row.getString("STATUS_VERSAO_FICHA")),
            text(row.getString("U_COLECAO")), text(row.getString("DESC_COLECAO")));
    }
    private List<String> key(Map<String, Object> row) {
        return List.of(String.valueOf(row.get("MATERIAL")).trim(), String.valueOf(row.get("ITEM")).trim());
    }
    private Map<String, Object> cleanRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(key, value instanceof String ? text(value) : value));
        return result;
    }
    private static String text(Object value) {
        if (value == null) return null;
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
