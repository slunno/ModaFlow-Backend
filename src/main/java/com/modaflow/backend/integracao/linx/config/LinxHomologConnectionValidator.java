package com.modaflow.backend.integracao.linx.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Guarda offline do perfil opt-in de homologação. Não usa o driver nem abre rede.
 * Aceita deliberadamente um subconjunto estrito da URL Microsoft JDBC: não tenta
 * interpretar escapes, aliases ou opções de roteamento. Credenciais ficam fora da URL.
 * Isso não remove permissões SQL nem audita referências indiretas em views/procedures.
 */
public final class LinxHomologConnectionValidator {
    public static final String HOMOLOG_DATABASE = "HOMOLOG_COBRANCA";
    public static final String MICROSOFT_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String AUTHORITY = "jdbc:sqlserver://192.168.1.6:1433";
    private static final Set<String> ALLOWED_PROPERTIES =
        Set.of("databasename", "encrypt", "trustservercertificate");

    private LinxHomologConnectionValidator() {}

    public static void validateBeforePool(LinxDataSourceProperties properties) {
        if (!properties.isHomolog6Only()) return;
        if (!HOMOLOG_DATABASE.equals(properties.getExpectedDatabase())) {
            throw invalid("O banco esperado deve ser HOMOLOG_COBRANCA.");
        }
        var source = properties.getDatasource();
        if (!MICROSOFT_DRIVER.equals(source.getDriverClassName())) {
            throw invalid("Use exclusivamente o driver Microsoft SQL Server esperado.");
        }
        String url = source.getUrl();
        if (url == null || url.isEmpty() || url.length() > 1024
            || url.chars().anyMatch(character -> Character.isWhitespace(character)
                || Character.isISOControl(character))
            || url.indexOf('{') >= 0 || url.indexOf('}') >= 0) {
            throw invalid("URL ausente ou sintaxe ambígua; escapes e espaços não são aceitos.");
        }
        String[] segments = url.split(";", -1);
        if (!AUTHORITY.equals(segments[0])) {
            throw invalid("Use apenas o endereço literal 192.168.1.6:1433.");
        }
        Map<String, String> values = new HashMap<>();
        for (int index = 1; index < segments.length; index++) {
            String segment = segments[index];
            // O driver admite o último delimitador; segmentos vazios internos são ambíguos.
            if (segment.isEmpty() && index == segments.length - 1) continue;
            int equals = segment.indexOf('=');
            if (equals <= 0 || equals == segment.length() - 1
                || segment.indexOf('=', equals + 1) >= 0) {
                throw invalid("Propriedade vazia ou sintaxe ambígua.");
            }
            String key = segment.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = segment.substring(equals + 1);
            if (!ALLOWED_PROPERTIES.contains(key)) {
                // Não incluir chave/URL recebidas: podem conter credenciais ou outros segredos.
                throw invalid("Propriedade não autorizada; credenciais e roteamento não pertencem à URL.");
            }
            if (values.putIfAbsent(key, value) != null) {
                throw invalid("Propriedades duplicadas não são aceitas, inclusive com caixa diferente.");
            }
        }
        if (!HOMOLOG_DATABASE.equals(values.get("databasename"))) {
            throw invalid("databaseName deve apontar explicitamente para HOMOLOG_COBRANCA.");
        }
        if (!"true".equalsIgnoreCase(values.get("encrypt"))) {
            throw invalid("encrypt=true é obrigatório.");
        }
        String trustCertificate = values.get("trustservercertificate");
        if (trustCertificate != null
            && !"true".equalsIgnoreCase(trustCertificate)
            && !"false".equalsIgnoreCase(trustCertificate)) {
            throw invalid("trustServerCertificate deve ser um booleano explícito.");
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Conexão Linx homolog6 bloqueada antes do pool. " + reason);
    }
}
