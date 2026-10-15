package com.modaflow.backend.integracao.linx.write;

import com.modaflow.backend.integracao.linx.config.LinxDataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Opt-in independente da consulta. Nenhuma credencial ou destino é herdado do leitor. */
@ConfigurationProperties("modaflow.linx-write")
public class LinxWriteProperties {
    private boolean enabled;
    private boolean reviewEnabled;
    private boolean homologAtual3;
    private String approvedSchemaSha256 = "";
    private final Map<Long, String> tenantGriffes = new HashMap<>();
    private final Set<Long> allowedUserIds = new HashSet<>();
    private final LinxDataSourceProperties.DataSource datasource = new LinxDataSourceProperties.DataSource();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isReviewEnabled() { return reviewEnabled; }
    public void setReviewEnabled(boolean value) { reviewEnabled = value; }
    public boolean isHomologAtual3() { return homologAtual3; }
    public void setHomologAtual3(boolean value) { homologAtual3 = value; }
    public String server() { return homologAtual3 ? "192.168.1.3" : "192.168.1.6"; }
    public String database() { return homologAtual3 ? "HOMOLOGACAO_ATUAL" : "HOMOLOG_COBRANCA"; }
    public String getApprovedSchemaSha256() { return approvedSchemaSha256; }
    public void setApprovedSchemaSha256(String value) { approvedSchemaSha256 = value; }
    public Map<Long, String> getTenantGriffes() { return tenantGriffes; }
    public Set<Long> getAllowedUserIds() { return allowedUserIds; }
    public LinxDataSourceProperties.DataSource getDatasource() { return datasource; }
}
