package com.modaflow.backend.ficha.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.integracao.linx.config.LinxDataSourceProperties;
import com.modaflow.backend.integracao.linx.config.LinxHomologConnectionValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FichaDraftProperties.class)
// Decisão de 07/10/2026: rascunho e histórico somente no navegador. Nenhuma flag reativa o pool.
@ConditionalOnExpression("false")
public class FichaDraftConfiguration {
    @Bean(destroyMethod = "close")
    public FichaDraftDatabaseClient fichaDraftDatabaseClient(FichaDraftProperties properties) {
        var source = properties.getDatasource();
        // Sempre força a guarda .6. Não há flag de bypass nesta funcionalidade.
        var guarded = new LinxDataSourceProperties();
        guarded.setHomolog6Only(true);
        guarded.setExpectedDatabase(LinxHomologConnectionValidator.HOMOLOG_DATABASE);
        guarded.getDatasource().setUrl(source.getUrl());
        guarded.getDatasource().setDriverClassName(source.getDriverClassName());
        LinxHomologConnectionValidator.validateBeforePool(guarded);
        if (properties.getMaximumDocumentBytes() == null || properties.getMaximumDocumentBytes() <= 0)
            throw new IllegalStateException("Configure maximum-document-bytes positivo para habilitar rascunhos.");
        requireText(source.getUsername(), "datasource.username");
        requireText(source.getPassword(), "datasource.password");
        if (source.getMaximumPoolSize() < 1 || source.getMinimumIdle() < 0
                || source.getMinimumIdle() > source.getMaximumPoolSize() || source.getConnectionTimeoutMs() < 250)
            throw new IllegalStateException("Configuração de pool de rascunho inválida.");

        var hikari = new HikariConfig();
        hikari.setPoolName("ModaFlow-Draft-Homolog6");
        hikari.setJdbcUrl(source.getUrl());
        hikari.setUsername(source.getUsername());
        hikari.setPassword(source.getPassword());
        hikari.setDriverClassName(source.getDriverClassName());
        hikari.setReadOnly(false); // Apenas tabelas modaflow; nunca cadastros ERP.
        hikari.setAutoCommit(true);
        hikari.setMaximumPoolSize(source.getMaximumPoolSize());
        hikari.setMinimumIdle(source.getMinimumIdle());
        hikari.setConnectionTimeout(source.getConnectionTimeoutMs());
        return new FichaDraftDatabaseClient(createDataSource(hikari));
    }

    @Bean public FichaDraftValidator fichaDraftValidator(ObjectMapper mapper, FichaDraftProperties properties) {
        return new FichaDraftValidator(mapper, properties.getMaximumDocumentBytes());
    }

    @Bean public FichaSqlRepository fichaSqlRepository(FichaDraftDatabaseClient client, ObjectMapper mapper,
                                                      FichaDraftValidator validator) {
        return new FichaSqlRepository(client.jdbc(), client.transaction(), mapper, validator);
    }

    @Bean public FichaDraftService fichaDraftService(FichaSqlRepository repository, ObjectMapper mapper,
                                                   FichaDraftValidator validator) {
        return new FichaDraftService(repository, repository, validator, Clock.systemUTC());
    }

    /** Test hook: entradas inválidas são recusadas antes de qualquer pool/socket. */
    protected HikariDataSource createDataSource(HikariConfig configuration) {
        return new HikariDataSource(configuration);
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank())
            throw new IllegalStateException("Persistência de rascunho habilitada sem configurar " + property + ".");
    }
}
