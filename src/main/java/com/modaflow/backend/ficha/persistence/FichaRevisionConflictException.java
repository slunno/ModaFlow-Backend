package com.modaflow.backend.ficha.persistence;

import java.util.Objects;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Identity;

/** Conflito do portal, distinto de VERSAO_FICHA no Linx. Sem conversão de identidade. */
public final class FichaRevisionConflictException extends RuntimeException {
    private final Identity identity;
    private final String revisaoEsperada;
    private final String revisaoAtual;

    public FichaRevisionConflictException(Identity identity, String revisaoEsperada, String revisaoAtual) {
        super("A ficha mudou ou já existe. Recarregue a revisão atual antes de gravar.");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.revisaoEsperada = revisaoEsperada;
        this.revisaoAtual = revisaoAtual;
    }

    public Identity identity() { return identity; }
    public String revisaoEsperada() { return revisaoEsperada; }
    public String revisaoAtual() { return revisaoAtual; }

    /**
     * Comparação pura: null esperado exige ausência, valor exige correspondência.
     * Não é lock/transação; o adaptador deverá fazê-la junto à gravação atômica.
     */
    public static void requireMatch(Identity identity, String expected, String current) {
        Objects.requireNonNull(identity, "identity");
        if (!Objects.equals(expected, current))
            throw new FichaRevisionConflictException(identity, expected, current);
    }
}
