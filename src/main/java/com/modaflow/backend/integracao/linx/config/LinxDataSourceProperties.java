package com.modaflow.backend.integracao.linx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modaflow.linx")
public class LinxDataSourceProperties {

    private boolean enabled;
    private boolean allowWriteCapableCredential = true;
    private String expectedDatabase = LinxDatabaseClient.REQUIRED_DATABASE;
    private boolean homolog6Only;
    private final DataSource datasource = new DataSource();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowWriteCapableCredential() {
        return allowWriteCapableCredential;
    }

    public void setAllowWriteCapableCredential(boolean allowWriteCapableCredential) {
        this.allowWriteCapableCredential = allowWriteCapableCredential;
    }

    public String getExpectedDatabase() {
        return expectedDatabase;
    }

    public void setExpectedDatabase(String expectedDatabase) {
        this.expectedDatabase = expectedDatabase;
    }

    public boolean isHomolog6Only() {
        return homolog6Only;
    }

    public void setHomolog6Only(boolean homolog6Only) {
        this.homolog6Only = homolog6Only;
    }

    public DataSource getDatasource() {
        return datasource;
    }

    public static class DataSource {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        private int maximumPoolSize = 3;
        private int minimumIdle = 0;
        private long connectionTimeoutMs = 10_000;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }
    }
}
