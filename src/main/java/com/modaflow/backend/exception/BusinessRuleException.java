package com.modaflow.backend.exception;

/**
 * EXCEÇÃO: BusinessRuleException
 * DESCRIÇÃO: Lançada quando uma regra de negócio do PLM é violada (ex: transição de etapa inválida).
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
