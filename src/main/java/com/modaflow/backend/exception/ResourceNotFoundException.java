package com.modaflow.backend.exception;

/**
 * EXCEÇÃO: ResourceNotFoundException
 * DESCRIÇÃO: Lançada quando um recurso solicitado (ID de peça, coleção, marca, etc.) não é encontrado no banco.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
