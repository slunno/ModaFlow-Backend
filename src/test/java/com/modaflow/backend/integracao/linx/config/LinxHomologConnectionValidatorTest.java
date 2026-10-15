package com.modaflow.backend.integracao.linx.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Somente objetos locais. Nenhuma aplicação Spring, pool real, driver ou socket é iniciado. */
class LinxHomologConnectionValidatorTest {
    private static final String URL =
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true";

    @Test
    void aceitaSomenteHomologacaoExplicitaComCriptografia() {
        assertThatCode(() -> LinxHomologConnectionValidator.validateBeforePool(properties(URL)))
            .doesNotThrowAnyException();
        assertThatCode(() -> LinxHomologConnectionValidator.validateBeforePool(
            properties(URL + ";trustServerCertificate=true;"))).doesNotThrowAnyException();
        assertThatCode(() -> LinxHomologConnectionValidator.validateBeforePool(
            properties(URL + ";trustServerCertificate=false"))).doesNotThrowAnyException();
        assertThatCode(() -> LinxHomologConnectionValidator.validateBeforePool(properties(
            "jdbc:sqlserver://192.168.1.6:1433;DATABASENAME=HOMOLOG_COBRANCA;Encrypt=TRUE")))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:sqlserver://192.168.1.3:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true",
        "jdbc:sqlserver://srv-sql-dev:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.6;databaseName=HOMOLOG_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1444;databaseName=HOMOLOG_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.6\\instance:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=KINGEJOE;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOGACAO_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=false",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=optional",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt={true}",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName={HOMOLOG_COBRANCA};encrypt=true",
        " jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433; databaseName=HOMOLOG_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true;\n",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true=",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true;trustServerCertificate=yes"
    })
    void rejeitaDestinoCriptografiaOuSintaxeInseguraAntesDeCriarPool(String url) {
        var config = new NoPoolConfig();
        assertThatThrownBy(() -> config.linxDatabaseClient(properties(url)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bloqueada antes do pool");
        assertThat(config.poolRequested).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "serverName=192.168.1.3", "portNumber=1434", "instanceName=other",
        "failoverPartner=192.168.1.3", "multiSubnetFailover=true",
        "applicationIntent=ReadOnly", "transparentNetworkIPResolution=true",
        "socketFactoryClass=custom.Factory", "serverCertificate=custom.pem",
        "user=placeholder", "username=placeholder", "uid=placeholder",
        "password=synthetic-secret", "accessToken=synthetic-secret", "authentication=SqlPassword",
        "database=KINGEJOE", "unknownProperty=value",
        "encrypt=false", "ENCRYPT=true", "DatabaseName=HOMOLOG_COBRANCA",
        "trustServerCertificate=true;TRUSTSERVERCERTIFICATE=false"
    })
    void rejeitaOverrideAliasesCredenciaisOuDuplicatas(String suffix) {
        var config = new NoPoolConfig();
        assertThatThrownBy(() -> config.linxDatabaseClient(properties(URL + ";" + suffix)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bloqueada antes do pool")
            .hasMessageNotContaining("synthetic-secret")
            .hasMessageNotContaining(URL);
        assertThat(config.poolRequested).isFalse();
    }

    @Test
    void rejeitaDriverOuBancoEsperadoDiferentes() {
        var wrongDriver = properties(URL);
        wrongDriver.getDatasource().setDriverClassName("org.h2.Driver");
        var config = new NoPoolConfig();
        assertThatThrownBy(() -> config.linxDatabaseClient(wrongDriver))
            .isInstanceOf(IllegalStateException.class);
        var wrongDatabase = properties(URL);
        wrongDatabase.setExpectedDatabase("HOMOLOGACAO_COBRANCA");
        assertThatThrownBy(() -> config.linxDatabaseClient(wrongDatabase))
            .isInstanceOf(IllegalStateException.class);
        assertThat(config.poolRequested).isFalse();
    }

    @Test
    void rejeitaUrlNulaSemIniciarPool() {
        var config = new NoPoolConfig();
        assertThatThrownBy(() -> config.linxDatabaseClient(properties(null)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(config.poolRequested).isFalse();
    }

    @Test
    void defaultsAntigosNaoAtivamNemRedirecionamHomologacao() {
        var properties = new LinxDataSourceProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isHomolog6Only()).isFalse();
        assertThat(properties.getExpectedDatabase()).isEqualTo("HOMOLOGACAO_ATUAL");
        assertThatCode(() -> LinxHomologConnectionValidator.validateBeforePool(properties))
            .doesNotThrowAnyException();
    }

    @Test
    void clienteUsaBancoEsperadoConfiguradoSemAbrirConexao() {
        HikariDataSource source = mock(HikariDataSource.class);
        var legacy = new LinxDatabaseClient(source, true);
        var homolog = new LinxDatabaseClient(source, true, "HOMOLOG_COBRANCA");
        assertThat(legacy.expectedDatabase()).isEqualTo("HOMOLOGACAO_ATUAL");
        assertThat(homolog.expectedDatabase()).isEqualTo("HOMOLOG_COBRANCA");
        verifyNoInteractions(source);
    }

    @Test
    void perfilHomologacaoPermaneceOptInEIsolaAsVariaveisDoLinx() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-homolog6.yml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("modaflow.linx.enabled"))
            .isEqualTo("${MODAFLOW_LINX_HOMOLOG6_ENABLED:false}");
        assertThat(properties.getProperty("modaflow.linx.homolog6-only")).isEqualTo("true");
        assertThat(properties.getProperty("modaflow.linx.expected-database"))
            .isEqualTo("HOMOLOG_COBRANCA");
        assertThat(properties.getProperty("modaflow.linx.datasource.url"))
            .isEqualTo("${MODAFLOW_LINX_HOMOLOG6_DATASOURCE_URL:}");
        assertThat(properties.getProperty("modaflow.linx.datasource.username"))
            .isEqualTo("${MODAFLOW_LINX_HOMOLOG6_DATASOURCE_USERNAME:}");
        assertThat(properties.getProperty("modaflow.linx.datasource.password"))
            .isEqualTo("${MODAFLOW_LINX_HOMOLOG6_DATASOURCE_PASSWORD:}");
        assertThat(properties.getProperty("modaflow.linx.datasource.driver-class-name"))
            .isEqualTo(LinxHomologConnectionValidator.MICROSOFT_DRIVER);
        assertThat(properties.stringPropertyNames())
            .noneMatch(key -> key.startsWith("spring.datasource") || key.startsWith("spring.jpa"));
    }

    private LinxDataSourceProperties properties(String url) {
        var properties = new LinxDataSourceProperties();
        properties.setHomolog6Only(true);
        properties.setExpectedDatabase("HOMOLOG_COBRANCA");
        properties.getDatasource().setUrl(url);
        // Só marcadores sintéticos: nenhum arquivo de credencial é lido pelos testes.
        properties.getDatasource().setUsername("test-placeholder");
        properties.getDatasource().setPassword("test-placeholder");
        return properties;
    }

    private static class NoPoolConfig extends LinxDataSourceConfig {
        boolean poolRequested;

        @Override
        protected HikariDataSource createDataSource(HikariConfig configuration) {
            poolRequested = true;
            throw new AssertionError("Teste offline não pode construir o pool.");
        }
    }
}
