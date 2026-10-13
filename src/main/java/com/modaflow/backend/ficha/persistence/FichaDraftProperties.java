package com.modaflow.backend.ficha.persistence;

import com.modaflow.backend.integracao.linx.config.LinxDataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração exclusiva do documento ModaFlow; não altera o DataSource JPA/Linx. */
@ConfigurationProperties(prefix = "modaflow.ficha.persistence")
public final class FichaDraftProperties {
    private boolean enabled;
    // Sem tamanho presumido: o limite técnico deve ser informado ao habilitar.
    private Long maximumDocumentBytes;
    private final LinxDataSourceProperties.DataSource datasource = new LinxDataSourceProperties.DataSource();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getMaximumDocumentBytes() { return maximumDocumentBytes; }
    public void setMaximumDocumentBytes(Long value) { maximumDocumentBytes = value; }
    public LinxDataSourceProperties.DataSource getDatasource() { return datasource; }
}
