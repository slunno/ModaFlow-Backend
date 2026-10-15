package com.modaflow.backend.integracao.linx.write;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static com.modaflow.backend.integracao.linx.write.LinxWriteContracts.*;

/** Somente campos com semântica conhecida. Não recebe nomes SQL livres nem dados fiscais. */
final class LinxWriteRules {
    static final String PRODUCT = "PRODUTOS", MATERIAL = "PRODUTO_VERSAO_MATERIAL",
        MATERIAL_COLOR = "PRODUTO_VERSAO_MATERIAL_COR", COLOR = "PRODUTO_CORES";
    static final Map<String, Set<String>> KEYS = Map.of(
        PRODUCT, Set.of(), MATERIAL, Set.of("MATERIAL", "ITEM"),
        MATERIAL_COLOR, Set.of("MATERIAL", "ITEM", "COR_PRODUTO"), COLOR, Set.of("COR_PRODUTO"));
    static final Set<String> CONSUMPTIONS = new HashSet<>(IntStream.rangeClosed(1,48).mapToObj(i -> "C"+i).toList());
    static final Set<String> MATERIAL_FIELDS;
    static {
        var fields = new HashSet<>(CONSUMPTIONS);
        fields.add("DESC_USO_MATERIAL");
        MATERIAL_FIELDS = Set.copyOf(fields);
    }
    static Map<String, Object> normalize(Patch patch) {
        if (patch != null && (PRODUCT.equals(patch.tabela()) || COLOR.equals(patch.tabela())))
            throw invalid("Cadastro global compartilhado: nome/cores do produto não podem ser alterados pelo fluxo de uma coleção.");
        if (patch == null || !KEYS.containsKey(patch.tabela()) || patch.chave() == null
                || !patch.chave().keySet().equals(KEYS.get(patch.tabela())) || patch.valores() == null)
            throw invalid("Tabela ou chave da alteração não autorizada.");
        for (var key : patch.chave().entrySet())
            text(key.getValue(), switch (key.getKey()) { case "MATERIAL" -> 11; case "ITEM" -> 4; default -> 10; }, key.getKey(), false);
        if (patch.excluir()) {
            if (!patch.descarteConfirmado() || !patch.valores().isEmpty()
                    || !(MATERIAL.equals(patch.tabela()) || MATERIAL_COLOR.equals(patch.tabela())))
                throw invalid("Descarte exige confirmação explícita e só pode remover o material ou vínculo de cor desta versão.");
            return Map.of();
        }
        if (patch.valores().isEmpty()) throw invalid("Alteração sem campos.");
        Set<String> allowed = switch(patch.tabela()) {
            case PRODUCT -> Set.of("DESC_PRODUTO");
            case MATERIAL -> MATERIAL_FIELDS;
            case MATERIAL_COLOR -> Set.of("COR_MATERIAL");
            case COLOR -> Set.of("COR");
            default -> Set.of();
        };
        var result = new TreeMap<String, Object>();
        for (var entry : patch.valores().entrySet()) {
            var field = entry.getKey(); var value = entry.getValue();
            if (!allowed.contains(field)) throw invalid("Campo sem destino de escrita aprovado: " + field + ".");
            if (CONSUMPTIONS.contains(field)) {
                if (!(value instanceof String || value instanceof Number)
                        || !value.toString().matches("\\d+(?:[.,]\\d{1,3})?"))
                    throw invalid(field + ": use consumo não negativo com até 3 casas decimais, sem arredondamento automático.");
                var decimal = new BigDecimal(value.toString().replace(',', '.'));
                if (decimal.compareTo(new BigDecimal("999999.999")) > 0) throw invalid(field + ": consumo excede numeric(9,3).");
                result.put(field, decimal);
            } else {
                if (!(value instanceof String string)) throw invalid(field + ": informe texto válido, não null.");
                result.put(field, text(string, field.equals("DESC_PRODUTO") ? 100 : field.equals("DESC_USO_MATERIAL") ? 80 : 10,
                    field, field.equals("DESC_USO_MATERIAL")));
            }
        }
        return Collections.unmodifiableMap(result);
    }
    static String text(String value, int bytes, String field, boolean empty) {
        // Restrição conservadora UTF-8: nunca trunca varchar/char ou depende da codepage do cliente.
        if (value == null || (!empty && value.isBlank()) || value.getBytes(StandardCharsets.UTF_8).length > bytes
                || value.chars().anyMatch(c -> Character.isISOControl(c)))
            throw invalid(field + ": valor ausente, inválido ou maior que " + bytes + " bytes.");
        return value;
    }
    static boolean equal(Object left, Object right) {
        if (left instanceof byte[] a && right instanceof byte[] b) return Arrays.equals(a,b);
        if (left instanceof Number && right instanceof Number)
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        if (left instanceof String a && right instanceof String b) return a.stripTrailing().equals(b.stripTrailing());
        return Objects.equals(left, right);
    }
    static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
    static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
