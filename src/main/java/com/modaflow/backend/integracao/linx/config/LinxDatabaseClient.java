package com.modaflow.backend.integracao.linx.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Mantém o pool Linx encapsulado para que ele não seja confundido com o
 * DataSource primário usado pelo JPA da aplicação.
 */
public final class LinxDatabaseClient implements AutoCloseable {

    public static final String REQUIRED_DATABASE = "HOMOLOGACAO_ATUAL";

    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean allowWriteCapableCredential;
    private final String expectedDatabase;

    public LinxDatabaseClient(HikariDataSource dataSource, boolean allowWriteCapableCredential) {
        this(dataSource, allowWriteCapableCredential, REQUIRED_DATABASE);
    }

    public LinxDatabaseClient(
        HikariDataSource dataSource,
        boolean allowWriteCapableCredential,
        String expectedDatabase
    ) {
        if (expectedDatabase == null || expectedDatabase.isBlank()) {
            throw new IllegalArgumentException("Configure o banco esperado da integração Linx.");
        }
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.allowWriteCapableCredential = allowWriteCapableCredential;
        this.expectedDatabase = expectedDatabase;
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    public String expectedDatabase() {
        return expectedDatabase;
    }

    public boolean allowWriteCapableCredential() {
        return allowWriteCapableCredential;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
