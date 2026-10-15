package com.modaflow.backend.integracao.linx.write;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Function;

/** Pool encapsulado, sem substituir o DataSource JPA ou o conector somente leitura. */
public class LinxWriteDatabase implements AutoCloseable {
    private final HikariDataSource source;
    private final NamedParameterJdbcTemplate jdbc;
    private final DataSourceTransactionManager manager;
    public LinxWriteDatabase(HikariDataSource source) {
        this.source = source;
        jdbc = new NamedParameterJdbcTemplate(source);
        jdbc.getJdbcTemplate().setQueryTimeout(15);
        manager = new DataSourceTransactionManager(source);
    }
    public <T> T transaction(boolean readOnly, Function<NamedParameterJdbcTemplate, T> action) {
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        tx.setTimeout(30);
        tx.setReadOnly(readOnly);
        return tx.execute(status -> action.apply(jdbc));
    }
    public void close() { source.close(); }
}
