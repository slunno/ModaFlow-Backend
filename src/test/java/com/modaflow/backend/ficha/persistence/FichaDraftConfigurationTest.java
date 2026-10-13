package com.modaflow.backend.ficha.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FichaDraftConfigurationTest {
    private FichaDraftProperties properties() {
        var properties = new FichaDraftProperties();
        properties.setMaximumDocumentBytes(100_000L);
        properties.getDatasource().setUrl("jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true;trustServerCertificate=true");
        properties.getDatasource().setUsername("test-user");
        properties.getDatasource().setPassword("not-a-live-credential");
        return properties;
    }

    @Test void defaultsDisabledAndDoesNotPublishPoolRepositoryOrService() {
        assertThat(new FichaDraftProperties().isEnabled()).isFalse();
        new ApplicationContextRunner().withUserConfiguration(FichaDraftConfiguration.class, FichaDraftController.class)
                .run(context -> assertThat(context).doesNotHaveBean(FichaDraftDatabaseClient.class)
                        .doesNotHaveBean(FichaSqlRepository.class).doesNotHaveBean(FichaDraftService.class)
                        .doesNotHaveBean(FichaDraftController.class));
    }

    @Test void browserOnlyDecisionCannotBeReenabledByTheOldFlag() {
        new ApplicationContextRunner()
                .withUserConfiguration(FichaDraftConfiguration.class, FichaDraftController.class)
                .withPropertyValues("modaflow.ficha.persistence.enabled=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(FichaDraftConfiguration.class)
                        .doesNotHaveBean(FichaDraftProperties.class)
                        .doesNotHaveBean(FichaDraftDatabaseClient.class)
                        .doesNotHaveBean(FichaSqlRepository.class)
                        .doesNotHaveBean(FichaDraftService.class)
                        .doesNotHaveBean(FichaDraftController.class));
    }

    @Test void forbiddenConnectionIsRejectedBeforeConstructingPool() {
        var configuration = new PoolSpy();
        var properties = properties();
        properties.getDatasource().setUrl("jdbc:sqlserver://192.168.1.3:1433;databaseName=KINGEJOE;encrypt=true");
        assertThatThrownBy(() -> configuration.fichaDraftDatabaseClient(properties))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("bloqueada antes do pool");
        assertThat(configuration.configuration).isNull();
    }

    @Test void requiredLimitAndCredentialsAreCheckedBeforePool() {
        var configuration = new PoolSpy();
        var properties = properties();
        properties.setMaximumDocumentBytes(null);
        assertThatThrownBy(() -> configuration.fichaDraftDatabaseClient(properties)).isInstanceOf(IllegalStateException.class);
        properties.setMaximumDocumentBytes(-1L);
        assertThatThrownBy(() -> configuration.fichaDraftDatabaseClient(properties)).isInstanceOf(IllegalStateException.class);
        properties.setMaximumDocumentBytes(100L);
        properties.getDatasource().setPassword(null);
        assertThatThrownBy(() -> configuration.fichaDraftDatabaseClient(properties)).isInstanceOf(IllegalStateException.class);
        assertThat(configuration.configuration).isNull();
    }

    @Test void dedicatedHolderDoesNotImplementDataSourceAndHasIsolatedWritePool() {
        var configuration = new PoolSpy();
        var client = configuration.fichaDraftDatabaseClient(properties());
        assertThat(client).isNotInstanceOf(javax.sql.DataSource.class);
        assertThat(configuration.configuration.getPoolName()).isEqualTo("ModaFlow-Draft-Homolog6");
        assertThat(configuration.configuration.isReadOnly()).isFalse();
        assertThat(configuration.configuration.getJdbcUrl()).contains("192.168.1.6:1433");
        verifyNoInteractions(configuration.pool);
        client.close();
        verify(configuration.pool).close();
    }

    private static final class PoolSpy extends FichaDraftConfiguration {
        private HikariConfig configuration;
        private final HikariDataSource pool = mock(HikariDataSource.class);
        @Override protected HikariDataSource createDataSource(HikariConfig configuration) {
            this.configuration = configuration;
            return pool;
        }
    }
}
