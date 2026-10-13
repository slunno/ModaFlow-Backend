package com.modaflow.backend.ficha;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.peca.EtapaPecaEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class FichaConferenciaService {
    private static final Map<String, String> COMPONENTES = Map.of("ziper", "Zíper", "etiqueta", "Etiqueta", "elastico", "Elástico", "entretela", "Entretela");
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Resultado(boolean valida, List<String> pendencias) { }

    public Resultado conferir(FichaConferenciaRequest ficha) {
        List<String> erros = new ArrayList<>();
        if (ficha == null) return new Resultado(false, List.of("Envie a ficha para conferência."));
        List<String> tamanhos = ficha.tamanhos() == null ? List.of() : ficha.tamanhos();
        if (tamanhos.isEmpty() || tamanhos.size() > 48 || tamanhos.stream().anyMatch(x -> vazio(x))
                || new HashSet<>(tamanhos).size() != tamanhos.size()) erros.add("Selecione a grade completa, até 48 tamanhos sem repetições.");
        if (!ficha.gradeCompleta()) erros.add("Confirme que a grade não contém somente o piloto.");
        List<FichaConferenciaRequest.Variante> variantes = ficha.variantes() == null ? List.of() : ficha.variantes();
        if (variantes.isEmpty()) erros.add("Cadastre a variante Principal e seus insumos.");
        if (ficha.paraIntegracao() && (vazio(ficha.variantePrincipalId()) || variantes.size() != 1
                || variantes.getFirst() == null || !ficha.variantePrincipalId().equals(variantes.getFirst().id())))
            return new Resultado(false, List.of("A preparação Linx deve conter somente a Principal identificada. Outras variantes permanecem no portal."));
        Set<String> ids = new HashSet<>();
        for (var variante : variantes) {
            if (variante == null || vazio(variante.id()) || !ids.add(variante.id())) { erros.add("Variantes precisam de identificadores únicos."); continue; }
            String label = vazio(variante.nome()) ? "Variante sem nome" : variante.nome();
            var insumos = variante.insumos() == null ? List.<FichaConferenciaRequest.Insumo>of() : variante.insumos();
            String assinatura = assinatura(variante, tamanhos);
            for (var componente : COMPONENTES.entrySet()) {
                boolean presente = insumos.stream().filter(Objects::nonNull).anyMatch(item -> componente.getKey().equals(item.componente()));
                var decisao = variante.componentes() == null ? null : variante.componentes().get(componente.getKey());
                if (presente && decisao != null && Boolean.FALSE.equals(decisao.aplicavel()))
                    erros.add(label + ": " + componente.getValue() + " está cadastrado, mas foi marcado como não aplicável. Revise a confirmação ou remova o insumo explicitamente.");
                else if (!presente && (decisao == null || decisao.aplicavel() == null || !registroAtual(decisao.registro(), assinatura)))
                    erros.add(label + ": confirme se " + componente.getValue() + " se aplica à peça. A confirmação precisa corresponder aos insumos e à grade atuais.");
                else if (!presente && Boolean.TRUE.equals(decisao.aplicavel()))
                    erros.add(label + ": adicione e identifique o insumo de " + componente.getValue() + ", ou confirme que não se aplica.");
            }
            for (var item : insumos) {
                if (item == null) { erros.add(label + ": insumo inválido."); continue; }
                String nome = label + " / " + item.nome();
                if (!vazio(item.componente()) && !"outro".equals(item.componente()) && !COMPONENTES.containsKey(item.componente()))
                    erros.add(nome + ": selecione uma finalidade válida.");
                if ("elastico".equals(item.componente())) for (String tamanho : tamanhos) {
                    var consumo = tamanho == null || item.consumoPorTamanho() == null ? null : item.consumoPorTamanho().get(tamanho);
                    var valor = consumo == null ? null : decimal(consumo.valor());
                    if (valor == null)
                        erros.add(nome + ": preencha o consumo do elástico no tamanho " + tamanho + ". Vazio não significa que não é usado.");
                    else if (consumo.naoSeAplica())
                        erros.add(nome + ": tamanho " + tamanho + ", revise a dispensa antiga. Para não usar elástico, digite zero e confirme expressamente na conferência de entrega.");
                    else if (valor.signum() == 0 && !registroAtual(consumo.confirmacaoZero(), assinatura))
                        erros.add(nome + ": confirme expressamente que o elástico NÃO é usado no tamanho " + tamanho + " (consumo zero).");
                }
                if (!vazio(item.consumoGeral()) && decimal(item.consumoGeral()) == null)
                    erros.add(nome + ": informe consumo geral válido, sem separador de milhar.");
                if ((!vazio(item.preco()) || ficha.paraIntegracao()) && decimal(item.preco()) == null)
                    erros.add(nome + ": informe preço manual válido, sem separador de milhar.");
                if (ficha.paraIntegracao() && (vazio(item.codigo()) || vazio(item.unidade())))
                    erros.add(nome + ": informe código e unidade para preparar o vínculo Linx.");
            }
        }
        return new Resultado(erros.isEmpty(), List.copyOf(erros));
    }
    /** Mesmo retrato canônico do frontend. Não confia em uma assinatura atual enviada à parte. */
    static String assinatura(FichaConferenciaRequest.Variante variante, List<String> tamanhos) {
        var linhas = (variante.insumos() == null ? List.<FichaConferenciaRequest.Insumo>of() : variante.insumos()).stream()
                .filter(Objects::nonNull).sorted(Comparator.comparing(item -> texto(item.tipo()) + ":" + texto(item.id())))
                .map(item -> List.of(texto(item.tipo()), texto(item.id()), texto(item.nome()), texto(item.codigo()),
                        texto(item.componente()), texto(item.preco()), texto(item.unidade()), texto(item.consumoGeral()),
                        tamanhos.stream().map(tamanho -> {
                            var consumo = tamanho == null || item.consumoPorTamanho() == null ? null : item.consumoPorTamanho().get(tamanho);
                            return Arrays.asList(tamanho, consumo == null ? "" : texto(consumo.valor()), consumo != null && consumo.naoSeAplica());
                        }).toList())).toList();
        try { return JSON.writeValueAsString(Arrays.asList(variante.id(), tamanhos, linhas)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Não foi possível conferir o retrato dos insumos.", error); }
    }
    private static boolean registroAtual(FichaConferenciaRequest.Registro registro, String assinatura) {
        if (registro == null || !assinatura.equals(registro.assinaturaInsumos()) || vazio(registro.responsavelId()) || vazio(registro.responsavelNome()) || vazio(registro.registradoEm())) return false;
        if (!registro.registradoEm().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z")) return false;
        try { Instant.parse(registro.registradoEm()); return true; }
        catch (DateTimeParseException error) { return false; }
    }
    private static String texto(String value) { return value == null ? "" : value; }
    public void exigirValida(FichaConferenciaRequest ficha) {
        var resultado = conferir(ficha);
        if (!resultado.valida()) throw new BusinessRuleException(String.join("\n", resultado.pendencias()));
    }
    /** A conferência de entrega se aplica somente à passagem 01 -> 02. */
    public boolean exigeConferenciaNaTransicao(EtapaPecaEnum atual, EtapaPecaEnum destino) {
        return atual == EtapaPecaEnum.GERACAO_FICHA && destino == EtapaPecaEnum.ENGENHARIA_RECEBIMENTO;
    }
    /** A rota simples, inclusive no Kanban, não contorna a conferência de 01 -> 02. */
    public void exigirConferenciaNaMudanca(EtapaPecaEnum atual, EtapaPecaEnum destino) {
        if (exigeConferenciaNaTransicao(atual, destino))
            throw new BusinessRuleException("Confira a ficha na entrega de 01 geração de ficha para 02 engenharia recebimento. Salvar rascunho continua permitido.");
    }
    private static boolean vazio(String value) { return value == null || value.isBlank(); }
    private static BigDecimal decimal(String value) {
        if (vazio(value) || !value.trim().matches("\\d+(?:[.,]\\d{1,8})?")) return null;
        try {
            var result = new BigDecimal(value.trim().replace(',', '.'));
            return result.compareTo(new BigDecimal("1000000000000")) <= 0 ? result : null;
        } catch (NumberFormatException error) { return null; }
    }
}
