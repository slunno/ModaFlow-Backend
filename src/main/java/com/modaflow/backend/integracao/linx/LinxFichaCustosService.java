package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.integracao.linx.config.LinxDatabaseClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Expõe fontes distintas, sem escolher fornecedor/tabela comercial ou atribuir um total oficial.
 * O cálculo de insumos reproduz SOMENTE o ramo não semiacabado da view existente do Linx.
 */
@Service
@ConditionalOnProperty(prefix = "modaflow.linx", name = "enabled", havingValue = "true")
public class LinxFichaCustosService {
    private final NamedParameterJdbcTemplate jdbc;
    private final LinxIntegrationService integration;
    private final LinxFichaService fichas;

    public LinxFichaCustosService(LinxDatabaseClient client, LinxIntegrationService integration, LinxFichaService fichas) {
        this.jdbc = client.jdbc();
        this.integration = integration;
        this.fichas = fichas;
    }

    public LinxFichaCustosDTO getCustos(String produto, String versao, String griffe, String colecao,
                                      String descColecao, boolean legado) {
        return consultarCustos(produto, versao, griffe, colecao, descColecao, legado, false);
    }

    /** Integração completa, somente SELECT: todos os grupos e 48 posições físicas da ficha. */
    public LinxFichaCustosDTO getCustosCompletos(String produto, String versao, String griffe, String colecao,
                                               String descColecao, boolean legado) {
        return consultarCustos(produto, versao, griffe, colecao, descColecao, legado, true);
    }

    private LinxFichaCustosDTO consultarCustos(String produto, String versao, String griffe, String colecao,
                                              String descColecao, boolean legado, boolean completo) {
        if (versao == null || versao.trim().isEmpty() || versao.trim().length() > 5) {
            throw new BusinessRuleException("Versão obrigatória ou inválida.");
        }
        var connection = integration.requireExpectedDatabase();
        // Reutiliza a validação de identidade/coleção. Legado admite apenas a ficha atual, não histórico.
        var detalhe = legado ? fichas.getAtual(produto, griffe, colecao, descColecao)
            : fichas.getDetalhe(produto, versao, griffe, colecao, descColecao);
        if (!detalhe.versao().versao().equals(versao.trim())) {
            throw new ResourceNotFoundException("A versão solicitada não é a ficha atual desta peça/coleção.");
        }
        var params = new MapSqlParameterSource("produto", detalhe.versao().produto())
            .addValue("versao", detalhe.versao().versao()).addValue("griffe", griffe.trim());

        var avisos = new ArrayList<>(List.of(
            "Os preços de material, cor, fornecedor e produto são cadastros atuais, não snapshots da versão da ficha.",
            "A fórmula de insumos usa MATERIAIS_CORES.CUSTO_A_VISTA, não o preço geral do material nem o custo de fornecedor.",
            "Custo do consumo = Cn × preço à vista da cor ÷ fator de conversão × porcentagem de consumo ÷ 100. Não há arredondamento por linha.",
            "A expressão foi comprovada na view do Linx somente para posições 1 a 9 e subgrupos com IGNORAR_EM_FORMACAO_PRECO = 0.",
            "O portal identifica insumos por produto, versão, item e material. A view original junta consumo/cor apenas por produto, versão e item; itens reutilizados entre materiais ficam pendentes, sem estimativa.",
            "Os rótulos de tamanho vêm da grade atual do produto. Não foi encontrada uma grade histórica vinculada à versão.",
            "Operações, outros custos e tabelas comerciais são apresentados separadamente; não são somados aos insumos do portal.",
            "A lista de insumos do portal contém somente TECIDO e AVIAMENTOS. A view diagnóstica também pode incluir EMBALAGEM e outros grupos; os totais não são equivalentes.",
            "Terceiros e semiacabados não entram no cálculo de insumos do portal. Nenhum fornecedor ou tabela de preço é escolhido automaticamente.",
            "O diagnóstico original inclui serviços e sua agregação interna de consumo principal não separa versões. Não somar suas linhas nem tratar seu total como custo oficial do portal."
        ));
        if (completo) {
            avisos.clear();
            avisos.addAll(List.of(
                "Integração custos-v2: todos os grupos de materiais e as 48 posições da ficha, sem substituir rascunhos locais.",
                "Consumos pertencem à versão selecionada; preços, grade, operações e extras são os cadastros atuais do Linx, não snapshots históricos.",
                "Materiais são vinculados por produto, versão, item, material e cor. Custo = Cn × preço à vista da cor ÷ fator de conversão × porcentagem ÷ 100.",
                "As posições 10 a 48 aplicam a mesma fórmula proporcional aos consumos físicos da ficha; não são valores retornados pela view diagnóstica de nove posições.",
                "Operações usam CUSTO_SUGERIDO por fase e valor, sem multiplicação por tempo; rotas alternativas precisam ser selecionadas.",
                "Extras preservam CUSTO_TOTAL cadastrado. Semiacabados sem regra validada e dados ausentes permanecem pendentes, nunca zero automático.",
                "Preços do cadastro do produto e totais da view diagnóstica são fontes distintas do resumo da ficha."
            ));
        }
        if (legado && detalhe.versao().colecaoCodigo() == null) {
            avisos.add("Ficha legada sem U_COLECAO: vinculada apenas pelo cadastro atual do produto e pela coleção validada; não constitui histórico dessa coleção.");
        }

        var tamanhos = rows(TAMANHOS_SQL, params);
        var materiais = rows(completo ? MATERIAIS_COMPLETOS_SQL : MATERIAIS_SQL, params);
        if (materiais.stream().anyMatch(row -> "PENDENTE_VINCULO_ITEM".equals(row.get("STATUS_CALCULO")))) {
            avisos.add("Há item reutilizado entre materiais. A view Linx junta apenas produto/versão/item e pode cruzá-los. O portal mantém MATERIAL na identificação e não estima custo nesses casos.");
        }
        var fornecedores = rows(completo ? FORNECEDORES_SQL.replace(" AND M.GRUPO IN ('TECIDO', 'AVIAMENTOS')", "") : FORNECEDORES_SQL, params);
        var operacoes = rows(OPERACOES_SQL, params);
        var extras = rows(EXTRAS_SQL, params);
        var precos = rows(PRECOS_SQL, params);
        var diagnostico = diagnostico(params, avisos);
        return new LinxFichaCustosDTO(detalhe.versao(), connection.banco(), tamanhos, materiais,
            fornecedores, operacoes, extras, precos, diagnostico, List.copyOf(avisos), completo ? "custos-v2" : "custos-v1");
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource params) {
        return jdbc.queryForList(sql, params).stream().map(row -> {
            Map<String, Object> clean = new LinkedHashMap<>();
            row.forEach((key, value) -> clean.put(key, value instanceof String text ? nullIfBlank(text) : value));
            return clean;
        }).toList();
    }

    private static String nullIfBlank(String text) {
        var trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Map<String, Object>> diagnostico(MapSqlParameterSource params, List<String> avisos) {
        try {
            var audit = jdbc.queryForMap(DIAGNOSTICO_GUARD_SQL, new MapSqlParameterSource());
            if (!(audit.get("OBJETO_VALIDO") instanceof Number valid) || valid.intValue() != 1
                || !(audit.get("DEPENDENCIAS_INSEGURAS") instanceof Number unsafe) || unsafe.intValue() != 0) {
                avisos.add("Diagnóstico de custos indisponível: view ausente, dependência externa/não resolvida, synonym ou módulo opaco. Nenhuma dependência externa foi consultada.");
                return List.of();
            }
            return rows(DIAGNOSTICO_SQL, params);
        } catch (DataAccessException error) {
            // A fonte opcional pode faltar na réplica ou não ter permissão de metadados. As fontes obrigatórias não são ocultadas.
            avisos.add("Não foi possível validar/consultar a view diagnóstica de custos. Seus valores foram omitidos; as fontes de insumos permanecem disponíveis.");
            return List.of();
        }
    }

    private static final String TAMANHOS_SQL = """
        SELECT P.PRODUTO, P.GRADE, T.NUMERO_TAMANHOS, X.POSICAO,
               NULLIF(LTRIM(RTRIM(X.TAMANHO)), '') AS TAMANHO,
               'PRODUTOS.GRADE / PRODUTOS_TAMANHOS (cadastro atual)' AS FONTE
        FROM dbo.PRODUTOS P LEFT JOIN dbo.PRODUTOS_TAMANHOS T ON T.GRADE = P.GRADE
        CROSS APPLY (VALUES %s) X(POSICAO, TAMANHO)
        WHERE P.PRODUTO = :produto AND P.GRIFFE = :griffe
        ORDER BY X.POSICAO
        """.formatted(IntStream.rangeClosed(1, 48).mapToObj(i -> "(" + i + ", T.TAMANHO_" + i + ")")
            .collect(Collectors.joining(", ")));

    static final String MATERIAIS_SQL = materiaisSql(false);
    static final String MATERIAIS_COMPLETOS_SQL = materiaisSql(true);

    private static String materiaisSql(boolean completo) {
        return """
        SELECT V.PRODUTO, V.VERSAO_FICHA, V.ITEM, V.MATERIAL, M.DESC_MATERIAL, M.GRUPO,
               VC.COR_PRODUTO, PC.DESC_COR_PRODUTO, VC.COR_MATERIAL, MC.DESC_COR_MATERIAL,
               X.POSICAO, NULLIF(LTRIM(RTRIM(X.TAMANHO)), '') AS TAMANHO, X.CONSUMO,
               MC.CUSTO_A_VISTA AS PRECO_COR_A_VISTA, MC.CUSTO_REPOSICAO AS PRECO_COR_REPOSICAO,
               M.CUSTO_A_VISTA AS PRECO_MATERIAL_A_VISTA, M.CUSTO_REPOSICAO AS PRECO_MATERIAL_REPOSICAO,
               M.FATOR_CONVERSAO, VC.PORCENTAGEM_CONSUMO, M.UNID_FICHA_TEC, M.UNID_ESTOQUE,
               M.SEMI_ACABADO, MS.IGNORAR_EM_FORMACAO_PRECO, M.SUBGRUPO,
               M.VARIA_CUSTO_COR, M.VARIA_PRECO_COR, V.CONSUMO_P_TAMANHO, V.CONSUMO_FIXO,
               V.DESC_USO_MATERIAL, V.MATERIAL_PRINCIPAL, P.GRADE,
               J.ITEM_REUTILIZADO, ST.STATUS_CALCULO,
               CASE WHEN ST.STATUS_CALCULO = 'CALCULADO'
                    THEN X.CONSUMO * MC.CUSTO_A_VISTA / NULLIF(M.FATOR_CONVERSAO, 0)
                         * VC.PORCENTAGEM_CONSUMO / 100 END AS CUSTO_CONSUMO_LINX,
               'MATERIAIS_CORES.CUSTO_A_VISTA' AS FONTE_PRECO,
               'PRODUTO_VERSAO_MATERIAL.Cn' AS FONTE_CONSUMO,
               'Ramo nao semiacabado de W_KING_PRODUTO_CUSTO_FICHA_SINTETICO' AS FONTE_FORMULA
        FROM dbo.PRODUTO_VERSAO_MATERIAL V
        INNER JOIN dbo.PRODUTOS P ON P.PRODUTO = V.PRODUTO AND P.GRIFFE = :griffe
        INNER JOIN dbo.MATERIAIS M ON M.MATERIAL = V.MATERIAL
        LEFT JOIN dbo.PRODUTO_VERSAO_MATERIAL_COR VC ON VC.PRODUTO = V.PRODUTO
             AND VC.VERSAO_FICHA = V.VERSAO_FICHA AND VC.ITEM = V.ITEM AND VC.MATERIAL = V.MATERIAL
        LEFT JOIN dbo.PRODUTO_CORES PC ON PC.PRODUTO = VC.PRODUTO AND PC.COR_PRODUTO = VC.COR_PRODUTO
        LEFT JOIN dbo.MATERIAIS_CORES MC ON MC.MATERIAL = VC.MATERIAL AND MC.COR_MATERIAL = VC.COR_MATERIAL
        LEFT JOIN dbo.MATERIAIS_SUBGRUPO MS ON MS.GRUPO = M.GRUPO AND MS.SUBGRUPO = M.SUBGRUPO
        LEFT JOIN dbo.PRODUTOS_TAMANHOS T ON T.GRADE = P.GRADE
        CROSS APPLY (VALUES
            %s
        ) X(POSICAO,TAMANHO,CONSUMO)
        CROSS APPLY (SELECT CAST(CASE WHEN EXISTS (
            SELECT 1 FROM dbo.PRODUTO_VERSAO_MATERIAL V2
            WHERE V2.PRODUTO = V.PRODUTO AND V2.VERSAO_FICHA = V.VERSAO_FICHA
              AND V2.ITEM = V.ITEM AND V2.MATERIAL <> V.MATERIAL
        ) THEN 1 ELSE 0 END AS bit) AS ITEM_REUTILIZADO) J
        CROSS APPLY (SELECT CASE
            WHEN MS.IGNORAR_EM_FORMACAO_PRECO = 1 THEN 'FORA_FORMACAO_PRECO'
            WHEN M.SEMI_ACABADO = 1 THEN 'PENDENTE_SEMIACABADO'
            %s
            WHEN NULLIF(LTRIM(RTRIM(X.TAMANHO)), '') IS NULL THEN 'SEM_TAMANHO'
            WHEN MS.IGNORAR_EM_FORMACAO_PRECO IS NULL
                 OR X.CONSUMO IS NULL OR MC.CUSTO_A_VISTA IS NULL
                 OR M.FATOR_CONVERSAO IS NULL OR M.FATOR_CONVERSAO = 0
                 OR VC.PORCENTAGEM_CONSUMO IS NULL THEN 'PENDENTE_DADOS'
            ELSE 'CALCULADO' END AS STATUS_CALCULO) ST
        WHERE V.PRODUTO = :produto AND V.VERSAO_FICHA = :versao %s
        ORDER BY V.ITEM, V.MATERIAL, VC.COR_PRODUTO, VC.COR_MATERIAL, X.POSICAO
        """.formatted(
            IntStream.rangeClosed(1, completo ? 48 : 9).mapToObj(i -> "(" + i + ",T.TAMANHO_" + i + ",V.C" + i + ")")
                .collect(Collectors.joining(", ")),
            completo ? "" : "WHEN J.ITEM_REUTILIZADO = 1 THEN 'PENDENTE_VINCULO_ITEM'",
            completo ? "" : "AND M.GRUPO IN ('TECIDO', 'AVIAMENTOS')")
            .replace("Ramo nao semiacabado de W_KING_PRODUTO_CUSTO_FICHA_SINTETICO",
                completo ? "Formula proporcional por material/cor e posicao fisica - custos-v2"
                    : "Ramo nao semiacabado de W_KING_PRODUTO_CUSTO_FICHA_SINTETICO");
    }

    private static final String FORNECEDORES_SQL = """
        SELECT MF.MATERIAL, MF.COR_MATERIAL, MF.FORNECEDOR, F.COD_FORNECEDOR,
               MF.REF_FORNECEDOR, MF.DESC_FORNECEDOR, MF.REF_COR_FORNECEDOR, MF.DESC_COR_FORNECEDOR,
               MF.UNIDADE, MF.FATOR_CONVERSAO_FORNECEDOR, MF.CUSTO_REPOSICAO AS CUSTO_FORNECEDOR,
               MF.DIAS_ENTREGA, MF.OBS_FORNECEDOR, 'MATERIAIS_FORNECEDOR.CUSTO_REPOSICAO' AS FONTE
        FROM dbo.MATERIAIS_FORNECEDOR MF LEFT JOIN dbo.FORNECEDORES F ON F.FORNECEDOR = MF.FORNECEDOR
        WHERE EXISTS (SELECT 1 FROM dbo.PRODUTO_VERSAO_MATERIAL_COR VC
            INNER JOIN dbo.MATERIAIS M ON M.MATERIAL = VC.MATERIAL AND M.GRUPO IN ('TECIDO', 'AVIAMENTOS')
            WHERE VC.PRODUTO = :produto AND VC.VERSAO_FICHA = :versao
              AND VC.MATERIAL = MF.MATERIAL AND VC.COR_MATERIAL = MF.COR_MATERIAL)
        ORDER BY MF.MATERIAL, MF.COR_MATERIAL, MF.FORNECEDOR
        """;

    private static final String OPERACOES_SQL = """
        SELECT P.PRODUTO, R.TABELA_OPERACOES, R.ROTA_PRODUCAO, R.SEQUENCIA_PRODUTIVA,
               R.FASE_PRODUCAO, F.DESC_FASE_PRODUCAO, R.SETOR_PRODUCAO, S.DESC_SETOR_PRODUCAO,
               R.RECURSO_PRODUTIVO, RE.DESC_RECURSO, RE.NOME_CLIFOR, FO.COD_FORNECEDOR,
               R.PARTE_PRODUTO, R.UNIDADE_TEMPO, R.TEMPO_OPERACAO, R.TEMPO_PREPARACAO, R.TIPO_CUSTO,
               R.CUSTO_SUGERIDO, R.CUSTO_SUGERIDO_2, R.CUSTO_SUGERIDO_3, R.CUSTO_SUGERIDO_4,
               'PRODUTO_OPERACOES_ROTAS / cadastro atual' AS FONTE
        FROM dbo.PRODUTOS P
        INNER JOIN dbo.PRODUTO_OPERACOES_ROTAS R ON R.TABELA_OPERACOES = P.TABELA_OPERACOES
        LEFT JOIN dbo.PRODUCAO_FASE F ON F.FASE_PRODUCAO = R.FASE_PRODUCAO
        LEFT JOIN dbo.PRODUCAO_SETOR S ON S.FASE_PRODUCAO = R.FASE_PRODUCAO AND S.SETOR_PRODUCAO = R.SETOR_PRODUCAO
        LEFT JOIN dbo.PRODUCAO_RECURSOS RE ON RE.RECURSO_PRODUTIVO = R.RECURSO_PRODUTIVO
        LEFT JOIN dbo.FORNECEDORES FO ON FO.FORNECEDOR = RE.NOME_CLIFOR
        WHERE P.PRODUTO = :produto AND P.GRIFFE = :griffe
        ORDER BY R.ROTA_PRODUCAO, R.SEQUENCIA_PRODUTIVA, R.FASE_PRODUCAO, R.SETOR_PRODUCAO, R.RECURSO_PRODUTIVO
        """;

    private static final String EXTRAS_SQL = """
        SELECT E.PRODUTO, E.COD_OPERACAO_EXTRA, E.OPERACAO_EXTRA, E.SEQUENCIA,
               E.CUSTO_OPERACAO, E.QTDADE, E.CUSTO_TOTAL, E.TEMPO_HORAS,
               TE.OPERACAO_EXTRA AS NOME_CATALOGO, TE.TIPO_OPER_EXTRA, TE.CUSTO AS CUSTO_CATALOGO,
               'PRODUTOS_OPE_EXTRA / valores cadastrados, nao recalculados' AS FONTE
        FROM dbo.PRODUTOS_OPE_EXTRA E
        LEFT JOIN dbo.PRODUTOS_TAB_OPERACOES_EXTRAS TE ON TE.COD_OPERACAO_EXTRA = E.COD_OPERACAO_EXTRA
        WHERE E.PRODUTO = :produto
        ORDER BY E.SEQUENCIA, E.COD_OPERACAO_EXTRA, E.OPERACAO_EXTRA
        """;

    private static final String PRECOS_SQL = """
        SELECT 'PRODUTOS' AS FONTE, P.PRODUTO, CAST(NULL AS varchar(2)) AS CODIGO_TAB_PRECO,
               CAST(NULL AS varchar(100)) AS TABELA, CAST(NULL AS varchar(10)) AS COR_PRODUTO,
               CAST(NULL AS varchar(100)) AS FILIAL, CAST(NULL AS decimal(18,5)) AS PRECO1,
               CAST(NULL AS decimal(18,5)) AS PRECO2, CAST(NULL AS decimal(18,5)) AS PRECO3,
               CAST(NULL AS decimal(18,5)) AS PRECO4, CAST(NULL AS decimal(18,5)) AS U_PRECO_CUSTO,
               P.CUSTO_REPOSICAO1, P.CUSTO_REPOSICAO2, P.CUSTO_REPOSICAO3, P.CUSTO_REPOSICAO4
        FROM dbo.PRODUTOS P WHERE P.PRODUTO = :produto AND P.GRIFFE = :griffe
        UNION ALL
        SELECT 'PRODUTOS_PRECOS', PP.PRODUTO, PP.CODIGO_TAB_PRECO, TP.TABELA, NULL, NULL,
               PP.PRECO1, PP.PRECO2, PP.PRECO3, PP.PRECO4, PP.U_PRECO_CUSTO, NULL, NULL, NULL, NULL
        FROM dbo.PRODUTOS_PRECOS PP LEFT JOIN dbo.TABELAS_PRECO TP ON TP.CODIGO_TAB_PRECO = PP.CODIGO_TAB_PRECO
        WHERE PP.PRODUTO = :produto
        UNION ALL
        SELECT 'PRODUTOS_PRECO_COR', PP.PRODUTO, PP.CODIGO_TAB_PRECO, TP.TABELA, PP.COR_PRODUTO, NULL,
               PP.PRECO1, PP.PRECO2, PP.PRECO3, PP.PRECO4, NULL, NULL, NULL, NULL, NULL
        FROM dbo.PRODUTOS_PRECO_COR PP LEFT JOIN dbo.TABELAS_PRECO TP ON TP.CODIGO_TAB_PRECO = PP.CODIGO_TAB_PRECO
        WHERE PP.PRODUTO = :produto
        UNION ALL
        SELECT 'PRODUTOS_PRECO_FILIAL', PP.PRODUTO, PP.CODIGO_TAB_PRECO, TP.TABELA, PP.COR_PRODUTO, PP.FILIAL,
               PP.PRECO1, PP.PRECO2, PP.PRECO3, PP.PRECO4, NULL, NULL, NULL, NULL, NULL
        FROM dbo.PRODUTOS_PRECO_FILIAL PP LEFT JOIN dbo.TABELAS_PRECO TP ON TP.CODIGO_TAB_PRECO = PP.CODIGO_TAB_PRECO
        WHERE PP.PRODUTO = :produto
        ORDER BY FONTE, CODIGO_TAB_PRECO, COR_PRODUTO, FILIAL
        """;

    static final String DIAGNOSTICO_GUARD_SQL = """
        ;WITH D AS (
            SELECT O.object_id, CAST('/' + CONVERT(varchar(12), O.object_id) + '/' AS varchar(max)) AS caminho
            FROM sys.objects O WHERE O.object_id = OBJECT_ID('dbo.W_KING_PRODUTO_CUSTO_FICHA_SINTETICO', 'V')
            UNION ALL
            SELECT E.referenced_id, CAST(D.caminho + CONVERT(varchar(12), E.referenced_id) + '/' AS varchar(max))
            FROM D INNER JOIN sys.sql_expression_dependencies E ON E.referencing_id = D.object_id
            WHERE E.referenced_id IS NOT NULL AND E.referenced_class = 1
              AND D.caminho NOT LIKE '%/' + CONVERT(varchar(12), E.referenced_id) + '/%'
        )
        SELECT CASE WHEN OBJECT_ID('dbo.W_KING_PRODUTO_CUSTO_FICHA_SINTETICO', 'V') IS NOT NULL
                         AND HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION') = 1
                    THEN 1 ELSE 0 END AS OBJETO_VALIDO,
               (SELECT COUNT(*) FROM D INNER JOIN sys.objects O ON O.object_id = D.object_id
                LEFT JOIN sys.sql_modules M ON M.object_id = D.object_id
                WHERE O.type IN ('SN', 'FS', 'FT', 'AF')
                   OR (O.type IN ('V', 'FN', 'IF', 'TF') AND M.definition IS NULL)
                   OR M.definition LIKE '%OPENQUERY%' OR M.definition LIKE '%OPENROWSET%'
                   OR M.definition LIKE '%OPENDATASOURCE%')
               + (SELECT COUNT(*) FROM D INNER JOIN sys.sql_expression_dependencies E ON E.referencing_id = D.object_id
                  WHERE E.referenced_server_name IS NOT NULL
                     OR (E.referenced_database_name IS NOT NULL AND E.referenced_database_name <> DB_NAME())
                     OR (E.referenced_class = 1 AND E.referenced_id IS NULL)) AS DEPENDENCIAS_INSEGURAS
        OPTION (MAXRECURSION 50)
        """;

    private static final String DIAGNOSTICO_SQL = """
        SELECT V.PRODUTO, V.DESC_PRODUTO, V.COLECAO AS COLECAO_CADASTRO_ATUAL, V.VERSAO_FICHA, V.GRADE,
               V.COR_PRODUTO, V.DESC_COR_PRODUTO, V.MATERIAL, V.DESC_MATERIAL,
               V.CUSTO_MATERIAL1, V.CUSTO_MATERIAL2, V.CUSTO_MATERIAL3, V.CUSTO_MATERIAL4, V.CUSTO_MATERIAL5,
               V.CUSTO_MATERIAL6, V.CUSTO_MATERIAL7, V.CUSTO_MATERIAL8, V.CUSTO_MATERIAL9,
               V.ACABAMENTO, V.BORDADO, V.CASEADO_EXTERNO, V.COSTURA, V.ESTAMPARIA, V.LAVANDERIA,
               V.PREPARACAO, V.RAPPORT, V.REVISAO, V.SUBLIMACAO, V.CUSTO_EXTRA,
               V.TOTAL_CUSTO1, V.TOTAL_CUSTO2, V.TOTAL_CUSTO3, V.TOTAL_CUSTO4, V.TOTAL_CUSTO5,
               V.TOTAL_CUSTO6, V.TOTAL_CUSTO7, V.TOTAL_CUSTO8, V.TOTAL_CUSTO9,
               V.CONSUMO1, V.CONSUMO2, V.CONSUMO3, V.CONSUMO4, V.CONSUMO5,
               V.CONSUMO6, V.CONSUMO7, V.CONSUMO8, V.CONSUMO9,
               'W_KING_PRODUTO_CUSTO_FICHA_SINTETICO / diagnostico nao aprovado como total do portal' AS FONTE
        FROM dbo.W_KING_PRODUTO_CUSTO_FICHA_SINTETICO V
        WHERE V.PRODUTO = :produto AND V.VERSAO_FICHA = :versao
        ORDER BY V.COR_PRODUTO, V.MATERIAL
        """;
}
