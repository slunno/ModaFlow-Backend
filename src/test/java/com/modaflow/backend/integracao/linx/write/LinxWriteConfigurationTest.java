package com.modaflow.backend.integracao.linx.write;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinxWriteConfigurationTest {
    LinxWriteProperties properties() {
        var properties=new LinxWriteProperties();
        properties.setEnabled(true);
        properties.getDatasource().setUrl("jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true;trustServerCertificate=true");
        properties.getDatasource().setUsername("teste-restrito"); properties.getDatasource().setPassword("fixture-not-a-secret");
        properties.setApprovedSchemaSha256("0".repeat(64)); properties.getTenantGriffes().put(1L,"KING&JOE"); properties.getAllowedUserIds().add(9L);
        return properties;
    }
    @Test void destinoEPermissoesSaoValidadosAntesDeCriarPool() {
        assertThatCode(() -> LinxWriteConfiguration.validate(properties())).doesNotThrowAnyException();
        var wrong=properties(); wrong.getDatasource().setUrl(wrong.getDatasource().getUrl().replace("192.168.1.6","192.168.1.3"));
        assertThatThrownBy(() -> LinxWriteConfiguration.validate(wrong)).hasMessageContaining("antes do pool");
        var admin=properties(); admin.getDatasource().setUsername("sa");
        assertThatThrownBy(() -> LinxWriteConfiguration.validate(admin)).hasMessageContaining("não aceita sa");
        var hash=properties(); hash.setApprovedSchemaSha256("");
        assertThatThrownBy(() -> LinxWriteConfiguration.validate(hash)).hasMessageContaining("hash");
    }
    @Test void transacaoRealCommitSomenteNoSucessoERollbackNaFalha() throws Exception {
        var source=mock(HikariDataSource.class); var connection=mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var db=new LinxWriteDatabase(source);
        String result=db.transaction(false,jdbc -> "confirmado");
        assertThat(result).isEqualTo("confirmado");
        verify(connection).commit();
        assertThatThrownBy(() -> db.transaction(false,jdbc -> { throw new IllegalStateException("releitura falhou"); })).hasMessageContaining("releitura falhou");
        verify(connection).rollback();
        verify(connection,times(1)).commit();
        db.close();
    }
}
