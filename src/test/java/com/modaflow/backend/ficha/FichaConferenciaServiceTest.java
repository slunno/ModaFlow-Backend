package com.modaflow.backend.ficha;

import com.modaflow.backend.common.exception.BusinessRuleException;
import com.modaflow.backend.peca.EtapaPecaEnum;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FichaConferenciaServiceTest {
    private final FichaConferenciaService service = new FichaConferenciaService();
    static FichaConferenciaRequest confirmarAusentes(FichaConferenciaRequest ficha) {
        return new FichaConferenciaRequest(ficha.tamanhos(), ficha.gradeCompleta(), ficha.paraIntegracao(), ficha.variantes().stream().map(variante -> {
            var registro = new FichaConferenciaRequest.Registro("2026-10-07T12:00:00.000Z", "u", "Usuário teste", FichaConferenciaService.assinatura(variante, ficha.tamanhos()));
            Map<String, FichaConferenciaRequest.Decisao> decisoes = new HashMap<>();
            for (String componente : List.of("ziper", "etiqueta", "elastico", "entretela")) {
                boolean encontrado = variante.insumos().stream().anyMatch(item -> componente.equals(item.componente()));
                if (!encontrado) decisoes.put(componente, new FichaConferenciaRequest.Decisao(false, registro));
            }
            return new FichaConferenciaRequest.Variante(variante.id(), variante.nome(), decisoes, variante.insumos());
        }).toList());
    }
    private FichaConferenciaRequest ficha(String preco, Map<String, FichaConferenciaRequest.Consumo> consumo, boolean integracao) {
        var item = new FichaConferenciaRequest.Insumo("a", "Material de teste", "A1", "aviamentos", "elastico", preco, "Metros", "2", consumo);
        return confirmarAusentes(new FichaConferenciaRequest(List.of("P", "M"), true, integracao, List.of(new FichaConferenciaRequest.Variante("principal", "Principal", Map.of(), List.of(item)))));
    }
    private Map<String, FichaConferenciaRequest.Consumo> consumos() {
        return Map.of("P", new FichaConferenciaRequest.Consumo("0,5", false), "M", new FichaConferenciaRequest.Consumo("0,6", false));
    }
    @Test void conferePrecoManualSemConsultaSql() { assertTrue(service.conferir(ficha("0,3671", consumos(), true)).valida()); }
    @Test void rascunhoEntregaNaoExigemPrecoMasPreparacaoExige() {
        assertTrue(service.conferir(ficha("", consumos(), false)).valida());
        assertFalse(service.conferir(ficha("", consumos(), true)).valida());
    }
    @Test void zeroVazioENaoSeAplicaLegadoNaoLiberamElastico() {
        for (var consumo : List.of(new FichaConferenciaRequest.Consumo("0", false), new FichaConferenciaRequest.Consumo("", true), new FichaConferenciaRequest.Consumo("0", true), new FichaConferenciaRequest.Consumo("-1", false))) {
            var resultado = service.conferir(ficha("0", Map.of("P", consumo, "M", new FichaConferenciaRequest.Consumo("0,6", false)), false));
            assertFalse(resultado.valida());
            assertTrue(resultado.pendencias().stream().anyMatch(x -> x.contains("tamanho P")));
        }
    }
    @Test void rejeitaPrecoNegativoExpoenteEMilharMisto() {
        for (String preco : List.of("-1", "1e2", "1.234,56", "NaN")) assertFalse(service.conferir(ficha(preco, consumos(), true)).valida());
    }
    @Test void exigeConferenciaSomenteDeGeracaoParaEngenharia() {
        assertThrows(BusinessRuleException.class, () -> service.exigirConferenciaNaMudanca(EtapaPecaEnum.GERACAO_FICHA, EtapaPecaEnum.ENGENHARIA_RECEBIMENTO));
        assertDoesNotThrow(() -> service.exigirConferenciaNaMudanca(null, EtapaPecaEnum.GERACAO_FICHA));
        assertDoesNotThrow(() -> service.exigirConferenciaNaMudanca(EtapaPecaEnum.ENGENHARIA_RECEBIMENTO, EtapaPecaEnum.MODELAGEM));
        assertDoesNotThrow(() -> service.exigirConferenciaNaMudanca(EtapaPecaEnum.GERACAO_FICHA, EtapaPecaEnum.MODELAGEM));
        assertDoesNotThrow(() -> service.exigirConferenciaNaMudanca(EtapaPecaEnum.GERACAO_FICHA, EtapaPecaEnum.GERACAO_FICHA));
        assertDoesNotThrow(() -> service.exigirConferenciaNaMudanca(EtapaPecaEnum.ENGENHARIA_RECEBIMENTO, EtapaPecaEnum.GERACAO_FICHA));
    }
    @Test void fichaVaziaOuClassificacaoNulaNaoCausamErro500() {
        assertFalse(service.conferir(null).valida());
        var item = new FichaConferenciaRequest.Insumo("a", "Material", "A1", "aviamentos", null, "", "Metros", "", Map.of());
        var variante = new FichaConferenciaRequest.Variante("v", "Principal", Map.of(), List.of(item));
        assertFalse(service.conferir(new FichaConferenciaRequest(List.of(), false, false, List.of(variante))).valida());
    }
    @Test void outrosInsumosNaoExigemConsumoPorTamanhoMasAusentesExigemDecisao() {
        for (String tipo : List.of("tecidos", "aviamentos")) {
            var item = new FichaConferenciaRequest.Insumo("a", "Material", "A1", tipo, null, "10,50", "Metros", "1,2", null);
            var variante = new FichaConferenciaRequest.Variante("v", "Principal", null, List.of(item));
            var ficha = new FichaConferenciaRequest(List.of("Único"), true, true, List.of(variante));
            assertFalse(service.conferir(ficha).valida());
            assertEquals(4, service.conferir(ficha).pendencias().size());
            assertTrue(service.conferir(confirmarAusentes(ficha)).valida());
        }
    }
    @Test void retratoIgualAoContratoTypescript() {
        var ficha = ficha("0,3671", consumos(), false);
        assertEquals("[\"principal\",[\"P\",\"M\"],[[\"aviamentos\",\"a\",\"Material de teste\",\"A1\",\"elastico\",\"0,3671\",\"Metros\",\"2\",[[\"P\",\"0,5\",false],[\"M\",\"0,6\",false]]]]]", FichaConferenciaService.assinatura(ficha.variantes().getFirst(), ficha.tamanhos()));
    }
    @Test void zeroExigeRegistroAtualComAutorEData() {
        var ficha = ficha("1", Map.of("P", new FichaConferenciaRequest.Consumo("0", false), "M", new FichaConferenciaRequest.Consumo("0,6", false)), false);
        var variante = ficha.variantes().getFirst();
        var assinatura = FichaConferenciaService.assinatura(variante, ficha.tamanhos());
        for (var registro : List.of(
                new FichaConferenciaRequest.Registro("2026-10-07T12:00:00.000Z", "u", "Teste", assinatura),
                new FichaConferenciaRequest.Registro("2026-10-07T12:00:00.000Z", "", "Teste", assinatura),
                new FichaConferenciaRequest.Registro("não é data", "u", "Teste", assinatura),
                new FichaConferenciaRequest.Registro("2026-10-07T12:00:00.000Z", "u", "Teste", "antiga"))) {
            var item = variante.insumos().getFirst();
            var consumos = Map.of("P", new FichaConferenciaRequest.Consumo("0", false, registro), "M", new FichaConferenciaRequest.Consumo("0,6", false));
            var confirmado = new FichaConferenciaRequest.Insumo(item.id(), item.nome(), item.codigo(), item.tipo(), item.componente(), item.preco(), item.unidade(), item.consumoGeral(), consumos);
            var teste = new FichaConferenciaRequest(ficha.tamanhos(), true, false, List.of(new FichaConferenciaRequest.Variante(variante.id(), variante.nome(), variante.componentes(), List.of(confirmado))));
            assertEquals(registro.responsavelId().equals("u") && registro.registradoEm().startsWith("2026") && registro.assinaturaInsumos().equals(assinatura), service.conferir(teste).valida());
        }
    }
    @Test void cadaVarianteExigeSuaPropriaConfirmacao() {
        var ficha = ficha("1", consumos(), false);
        var principal = ficha.variantes().getFirst();
        var copia = new FichaConferenciaRequest.Variante("outra", "Outra", principal.componentes(), principal.insumos());
        var resultado = service.conferir(new FichaConferenciaRequest(ficha.tamanhos(), true, false, List.of(principal, copia)));
        assertFalse(resultado.valida());
        assertTrue(resultado.pendencias().stream().allMatch(x -> x.startsWith("Outra:")));
    }
    @Test void itemExistenteNaoPodeSerDispensadoESimSemItemNaoLibera() {
        var ficha = ficha("1", consumos(), false);
        var variante = ficha.variantes().getFirst();
        var decisoes = new HashMap<>(variante.componentes());
        var registro = decisoes.get("ziper").registro();
        decisoes.put("elastico", new FichaConferenciaRequest.Decisao(false, registro));
        decisoes.put("ziper", new FichaConferenciaRequest.Decisao(true, registro));
        var resultado = service.conferir(new FichaConferenciaRequest(ficha.tamanhos(), true, false, List.of(new FichaConferenciaRequest.Variante(variante.id(), variante.nome(), decisoes, variante.insumos()))));
        assertFalse(resultado.valida()); assertEquals(2, resultado.pendencias().size());
    }
    @Test void gradeNulaNaoCausaErro500() {
        var item = new FichaConferenciaRequest.Insumo("a", "Material", "A1", "aviamentos", "elastico", "", "Metros", "", Map.of());
        var variante = new FichaConferenciaRequest.Variante("v", "Principal", Map.of(), List.of(item));
        assertFalse(service.conferir(new FichaConferenciaRequest(Arrays.asList((String) null), false, false, List.of(variante))).valida());
    }
    @Test void integracaoRejeitaOutrasVariantesEPrincipalNaoIdentificada() {
        var ficha = ficha("1", consumos(), true);
        var principal = ficha.variantes().getFirst();
        var extra = new FichaConferenciaRequest.Variante("outra", "Outra", Map.of(), List.of());
        assertFalse(service.conferir(new FichaConferenciaRequest(ficha.tamanhos(), true, true, List.of(principal, extra), principal.id())).valida());
        assertFalse(service.conferir(new FichaConferenciaRequest(ficha.tamanhos(), true, true, List.of(principal), null)).valida());
        assertFalse(service.conferir(new FichaConferenciaRequest(ficha.tamanhos(), true, true, List.of(principal), "outra")).valida());
        assertTrue(service.conferir(new FichaConferenciaRequest(ficha.tamanhos(), true, true, List.of(principal), principal.id())).valida());
    }
}
