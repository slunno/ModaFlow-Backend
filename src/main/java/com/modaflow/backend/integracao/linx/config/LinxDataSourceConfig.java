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
        requireText(source.getUrl(), "MODAFLOW_LINX_DATASOURCE_URL");
        requireText(source.getUsername(), "MODAFLOW_LINX_DATASOURCE_USERNAME");
        requireText(source.getPassword(), "MODAFLOW_LINX_DATASOURCE_PASSWORD");

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
            new HikariDataSource(hikari),
            properties.isAllowWriteCapableCredential()
        );
    }

    private void requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Integração Linx habilitada sem configurar " + environmentVariable + "."
            );
        }
    }
}
