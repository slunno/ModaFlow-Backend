package com.modaflow.backend.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.ficha.FichaConferenciaRequest;
import com.modaflow.backend.ficha.FichaConferenciaService;
import java.util.List;
import java.util.Set;

/** Rebuild the established delivery contract from the persisted ficha, never a client verdict. */
final class WorkspaceConferencia {
    private WorkspaceConferencia() {}
    static void validate(ObjectMapper json,JsonNode data) {
        var ficha=data.path("fichaTecnica");var request=json.createObjectNode();
        request.set("tamanhos",ficha.path("tamanhos").isArray()?ficha.path("tamanhos"):json.createArrayNode());
        request.put("gradeCompleta",ficha.path("conferencia").path("gradeCompleta").asBoolean());
        request.put("paraIntegracao",false);var variants=request.putArray("variantes");
        for(var variant:ficha.path("variantes")) {
            var target=variants.addObject().put("id",variant.path("id").asText()).put("nome",variant.path("nome").asText());
            var components=target.putObject("componentes");
            ficha.path("conferencia").path("componentesPorVariante").path(variant.path("id").asText()).fields().forEachRemaining(entry->{
                var decision=components.putObject(entry.getKey());decision.set("aplicavel",entry.getValue().path("aplicavel"));decision.set("registro",registration(json,entry.getValue()));
            });
            var materials=target.putArray("insumos");
            for(String kind:List.of("tecidos","aviamentos")) for(var material:variant.path(kind)) {
                var item=materials.addObject();
                for(String key:List.of("id","nome","codigo","componente","unidade"))item.put(key,material.path(key).asText());
                item.put("tipo",kind).put("preco",material.path("custo").asText());
                var unit=material.path("unidade").asText().toLowerCase().trim();
                item.put("consumoGeral",material.path(kind.equals("aviamentos")?"quantidade":Set.of("kg","quilo","quilos","quilograma","quilogramas").contains(unit)?"peso":"consumo").asText());
                var consumption=item.putObject("consumoPorTamanho");
                material.path("consumoPorTamanho").fields().forEachRemaining(entry->{var value=entry.getValue();var v=consumption.putObject(entry.getKey()).put("valor",value.path("valor").asText()).put("naoSeAplica",value.path("naoSeAplica").asBoolean());if(value.has("confirmacaoZero"))v.set("confirmacaoZero",registration(json,value.path("confirmacaoZero")));});
            }
        }
        new FichaConferenciaService().exigirValida(json.convertValue(request,FichaConferenciaRequest.class));
    }
    private static JsonNode registration(ObjectMapper json,JsonNode value) {
        return json.createObjectNode().put("registradoEm",value.path("registradoEm").asText()).put("responsavelId",value.path("responsavel").path("id").asText()).put("responsavelNome",value.path("responsavel").path("nome").asText()).put("assinaturaInsumos",value.path("assinaturaInsumos").asText());
    }
}
