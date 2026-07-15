package com.walletriskscorer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5433/walletcache}")
    private String defaultJdbcUrl;

    @Value("${spring.datasource.username:sedisamuel}")
    private String defaultUsername;

    @Value("${spring.datasource.password:sam182006}")
    private String defaultPassword;

    @Bean
    public DataSource dataSource() throws URISyntaxException {
        HikariConfig config = new HikariConfig();

        if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            URI dbUri = new URI(databaseUrl);
            String userInfo = dbUri.getUserInfo();
            if (userInfo != null) {
                String[] credentials = userInfo.split(":", 2);
                config.setUsername(credentials[0]);
                if (credentials.length > 1) {
                    config.setPassword(credentials[1]);
                }
            }
            
            int port = dbUri.getPort() != -1 ? dbUri.getPort() : 5432;
            String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + port + dbUri.getPath();
            config.setJdbcUrl(dbUrl);
        } else {
            config.setJdbcUrl(defaultJdbcUrl);
            config.setUsername(defaultUsername);
            config.setPassword(defaultPassword);
        }

        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }
}
