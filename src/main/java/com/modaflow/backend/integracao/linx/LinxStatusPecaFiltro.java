package com.modaflow.backend.integracao.linx;

import com.modaflow.backend.peca.StatusPecaEnum;

import java.util.Set;

/** Status de negócio exibidos no filtro consolidado de peças. */
public enum LinxStatusPecaFiltro {
    EM_ANDAMENTO(Set.of(StatusPecaEnum.EM_DESENVOLVIMENTO, StatusPecaEnum.EM_PRODUCAO)),
    COMPLETA(Set.of(StatusPecaEnum.COMPLETA, StatusPecaEnum.APROVADO)),
    A_DESENHAR(Set.of(StatusPecaEnum.A_DESENHAR));

    private final Set<StatusPecaEnum> statuses;

    LinxStatusPecaFiltro(Set<StatusPecaEnum> statuses) {
        this.statuses = statuses;
    }

    public Set<StatusPecaEnum> statuses() {
        return statuses;
    }
}
