package com.modaflow.backend.ficha.persistence;

/** Mensagem não expõe credenciais, URL recebida ou dados do servidor divergente. */
public final class FichaDraftConnectionException extends IllegalStateException {
    public FichaDraftConnectionException() {
        super("Conexão de rascunho divergente da homologação autorizada; operação bloqueada.");
    }
}
