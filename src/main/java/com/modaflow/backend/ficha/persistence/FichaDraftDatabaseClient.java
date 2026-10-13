package com.modaflow.backend.ficha.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Holder, e não bean DataSource: o pool dedicado não substitui o primário do JPA. */
public final class FichaDraftDatabaseClient implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public FichaDraftDatabaseClient(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public NamedParameterJdbcTemplate jdbc() { return jdbc; }
    public TransactionTemplate transaction() { return transaction; }
    @Override public void close() { dataSource.close(); }
}
