package com.modaflow.backend.integracao.linx.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinxDataSourceProperties.class)
public class LinxDataSourceConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "modaflow.linx", name = "enabled", havingValue = "true")
    public LinxDatabaseClient linxDatabaseClient(LinxDataSourceProperties properties) {
        LinxDataSourceProperties.DataSource source = properties.getDatasource();
        // Validação puramente local: deve preceder até a construção do pool.
        LinxHomologConnectionValidator.validateBeforePool(properties);
        LinxHomologConnectionValidator.validateAtualBeforePool(properties);
        String sourcePrefix = properties.isHomolog6Only()
            ? "MODAFLOW_LINX_HOMOLOG6_DATASOURCE_" : "MODAFLOW_LINX_DATASOURCE_";
        requireText(source.getUrl(), sourcePrefix + "URL");
        requireText(source.getUsername(), sourcePrefix + "USERNAME");
        requireText(source.getPassword(), sourcePrefix + "PASSWORD");
        requireText(properties.getExpectedDatabase(), "modaflow.linx.expected-database");

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ModaFlow-Linx-ReadOnly");
        hikari.setJdbcUrl(source.getUrl());
        hikari.setUsername(source.getUsername());
        hikari.setPassword(source.getPassword());
        hikari.setDriverClassName(source.getDriverClassName());
        // Defesa adicional do pool: a API permanece limitada a consultas e as
        // permissões efetivas da credencial são auditadas pelo serviço Linx.
        hikari.setReadOnly(true);
        hikari.setAutoCommit(true);
        hikari.setMaximumPoolSize(Math.max(1, source.getMaximumPoolSize()));
        hikari.setMinimumIdle(Math.max(0, source.getMinimumIdle()));
        hikari.setConnectionTimeout(Math.max(250, source.getConnectionTimeoutMs()));

        return new LinxDatabaseClient(
            createDataSource(hikari),
            properties.isAllowWriteCapableCredential(),
            properties.getExpectedDatabase()
        );
    }

    /** Separado para comprovar, sem pool/socket em testes, a ordem da validação. */
    protected HikariDataSource createDataSource(HikariConfig configuration) {
        return new HikariDataSource(configuration);
    }

    private void requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Integração Linx habilitada sem configurar " + environmentVariable + "."
            );
        }
    }
}
