package com.modaflow.backend.ficha.persistence;

public final class FichaDocumentTooLargeException extends IllegalArgumentException {
    public FichaDocumentTooLargeException() { super("Documento ultrapassa o limite configurado do servidor."); }
}
