package com.modaflow.backend.integracao.linx;

import java.util.List;

/** Posições físicas da grade: posições vazias não são compactadas. */
public record LinxGradeDetalheDTO(String grade, int numeroTamanhos, List<Posicao> posicoes) {
    public record Posicao(int posicao, String tamanho) {}
}
