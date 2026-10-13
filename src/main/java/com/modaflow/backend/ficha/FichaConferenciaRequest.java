package com.modaflow.backend.ficha;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Rascunho de conferência. Não grava catálogo, ficha ou dados no ERP. */
public record FichaConferenciaRequest(
        @NotNull @Size(max = 48) List<String> tamanhos,
        boolean gradeCompleta,
        boolean paraIntegracao,
        @NotNull @Size(max = 100) List<@Valid Variante> variantes,
        String variantePrincipalId) {
    /** Compatibilidade dos chamadores Java; JSON deve identificar explicitamente a Principal. */
    public FichaConferenciaRequest(List<String> tamanhos, boolean gradeCompleta, boolean paraIntegracao,
                                  List<Variante> variantes) {
        this(tamanhos, gradeCompleta, paraIntegracao, variantes,
                variantes != null && !variantes.isEmpty() && variantes.getFirst() != null
                        ? variantes.getFirst().id() : null);
    }
    public record Registro(String registradoEm, String responsavelId, String responsavelNome,
                           String assinaturaInsumos) { }
    public record Decisao(@NotNull Boolean aplicavel, @Valid Registro registro) {
        public Decisao(Boolean aplicavel) { this(aplicavel, null); }
    }
    public record Consumo(String valor, boolean naoSeAplica, @Valid Registro confirmacaoZero) {
        public Consumo(String valor, boolean naoSeAplica) { this(valor, naoSeAplica, null); }
    }
    public record Variante(String id, String nome,
                           @Size(max = 4) Map<String, @Valid Decisao> componentes,
                           @NotNull @Size(max = 500) List<@Valid Insumo> insumos) { }
    public record Insumo(String id, String nome, String codigo, String tipo,
                         String componente, String preco, String unidade, String consumoGeral,
                         @Size(max = 48) Map<String, @Valid Consumo> consumoPorTamanho) { }
}
