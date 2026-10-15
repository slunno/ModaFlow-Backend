package com.modaflow.backend.integracao.linx.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Sem driver, pool, rede ou senha real. */
class LinxHomologAtualConnectionTest {
    private LinxDataSourceProperties source(String url) {
        var properties=new LinxDataSourceProperties();
        properties.getDatasource().setUrl(url); return properties;
    }
    @Test void aceitaSomenteDestinoAtualConfirmadoComCriptografia() {
        assertThatCode(() -> LinxHomologConnectionValidator.validateAtualBeforePool(source(
            "jdbc:sqlserver://192.168.1.3:1433;databaseName=HOMOLOGACAO_ATUAL;encrypt=true;trustServerCertificate=true"))).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={
        "jdbc:sqlserver://192.168.1.3:1433;databaseName=KINGEJOE;encrypt=true",
        "jdbc:sqlserver://192.168.1.3:1433;databaseName=HOMOLOGACAO_COBRANCA;encrypt=true",
        "jdbc:sqlserver://192.168.1.3:1433;databaseName=HOMOLOG_ATUAL;encrypt=true",
        "jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOGACAO_ATUAL;encrypt=true",
        "jdbc:sqlserver://192.168.1.3:1433;databaseName=HOMOLOGACAO_ATUAL;encrypt=false",
        "jdbc:sqlserver://192.168.1.3:1433;databaseName=HOMOLOGACAO_ATUAL;encrypt=true;failoverPartner=192.168.1.6"
    }) void naoPermiteBancoAnteriorProducaoOuRedirecionamento(String url) {
        assertThatThrownBy(() -> LinxHomologConnectionValidator.validateAtualBeforePool(source(url)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("antes do pool");
    }
    @Test void perfilHomolog6MantemSeuIsolamentoExplicito() {
        var properties=source("jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true");
        properties.setHomolog6Only(true); properties.setExpectedDatabase("HOMOLOG_COBRANCA");
        assertThatCode(() -> { LinxHomologConnectionValidator.validateBeforePool(properties);
            LinxHomologConnectionValidator.validateAtualBeforePool(properties); }).doesNotThrowAnyException();
    }
}
