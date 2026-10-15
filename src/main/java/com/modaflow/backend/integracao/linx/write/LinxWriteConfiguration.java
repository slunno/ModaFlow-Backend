package com.modaflow.backend.integracao.linx.write;

import com.modaflow.backend.integracao.linx.config.LinxDataSourceProperties;
import com.modaflow.backend.integracao.linx.config.LinxHomologConnectionValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinxWriteProperties.class)
public class LinxWriteConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("${modaflow.linx-write.enabled:false} or ${modaflow.linx-write.review-enabled:false}")
    public LinxWriteDatabase linxWriteDatabase(LinxWriteProperties properties) {
        validate(properties);
        var source = properties.getDatasource();
        var pool = new HikariConfig();
        pool.setPoolName("ModaFlow-Linx-Homolog-Writer");
        pool.setJdbcUrl(source.getUrl());
        pool.setUsername(source.getUsername());
        pool.setPassword(source.getPassword());
        pool.setDriverClassName(source.getDriverClassName());
        pool.setMaximumPoolSize(2);
        pool.setMinimumIdle(0);
        pool.setReadOnly(!properties.isEnabled());
        pool.setConnectionTimeout(10_000);
        return new LinxWriteDatabase(new HikariDataSource(pool));
    }

    /** Não instancia driver/pool nem abre socket antes de validar o destino literal. */
    static void validate(LinxWriteProperties properties) {
        var source = properties.getDatasource();
        var guard = new LinxDataSourceProperties();
        guard.setHomolog6Only(!properties.isHomologAtual3());
        guard.setExpectedDatabase(properties.database());
        guard.getDatasource().setUrl(source.getUrl());
        guard.getDatasource().setDriverClassName(source.getDriverClassName());
        if (properties.isHomologAtual3()) LinxHomologConnectionValidator.validateAtualBeforePool(guard);
        else LinxHomologConnectionValidator.validateBeforePool(guard);
        if (source.getUsername() == null || source.getUsername().isBlank()
                || source.getPassword() == null || source.getPassword().isBlank())
            throw new IllegalStateException("Configure credencial exclusiva do gravador de homologação.");
        if ("sa".equalsIgnoreCase(source.getUsername().trim()))
            throw new IllegalStateException("O gravador não aceita sa. Use usuário restrito ao banco de homologação.");
        if (properties.isEnabled() && !properties.getApprovedSchemaSha256().matches("[a-fA-F0-9]{64}"))
            throw new IllegalStateException("Informe o hash de esquema/dependências revisado e aprovado antes de habilitar a escrita.");
        if (properties.getAllowedUserIds().isEmpty() || properties.getTenantGriffes().isEmpty()
                || properties.getTenantGriffes().values().stream().anyMatch(value -> !LinxWriteService.GRIFFES.contains(value)))
            throw new IllegalStateException("Configure o vínculo explícito entre marcas do portal e griffes do ERP.");
    }
}
