package com.modaflow.backend.integracao.linx.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Mantém o pool Linx encapsulado para que ele não seja confundido com o
 * DataSource primário usado pelo JPA da aplicação.
 */
public final class LinxDatabaseClient implements AutoCloseable {

    public static final String REQUIRED_DATABASE = "HOMOLOGACAO_COBRANCA";

    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean allowWriteCapableCredential;

    public LinxDatabaseClient(HikariDataSource dataSource, boolean allowWriteCapableCredential) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.allowWriteCapableCredential = allowWriteCapableCredential;
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    public String expectedDatabase() {
        return REQUIRED_DATABASE;
    }

    public boolean allowWriteCapableCredential() {
        return allowWriteCapableCredential;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
