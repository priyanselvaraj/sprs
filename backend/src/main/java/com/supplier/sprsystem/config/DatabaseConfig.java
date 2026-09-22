package com.supplier.sprsystem.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Value("${spring.datasource.url:#{null}}")
    private String springDatasourceUrl;

    @Value("${spring.datasource.username:${DB_USERNAME:root}}")
    private String configuredUsername;

    @Value("${spring.datasource.password:${DB_PASSWORD:root}}")
    private String configuredPassword;

    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:${DB_MAX_POOL_SIZE:5}}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:${DB_MIN_IDLE:2}}")
    private int minIdle;

    @Bean
    @Primary
    public DataSource dataSource() {
        String rawUrl = StringUtils.hasText(databaseUrl) ? databaseUrl.trim() : springDatasourceUrl;
        if (!StringUtils.hasText(rawUrl)) {
            rawUrl = "jdbc:mysql://localhost:3306/pri?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);
        config.setConnectionTimeout(20000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("SPRS-HikariCP");

        String effectiveUser = configuredUsername;
        String effectivePass = configuredPassword;
        String jdbcUrl = rawUrl;

        // Check if rawUrl is in cloud URI format (e.g. mysql://user:pass@host:port/dbname?ssl-mode=REQUIRED)
        if (rawUrl.startsWith("mysql://") || rawUrl.startsWith("jdbc:mysql://") && rawUrl.contains("@")) {
            try {
                String uriString = rawUrl.startsWith("jdbc:") ? rawUrl.substring(5) : rawUrl;
                URI uri = new URI(uriString);

                String userInfo = uri.getUserInfo();
                if (StringUtils.hasText(userInfo) && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    effectiveUser = parts[0];
                    effectivePass = parts[1];
                }

                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 3306;
                String path = uri.getPath();
                if (!StringUtils.hasText(path) || "/".equals(path)) {
                    path = "/defaultdb";
                }
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }

                StringBuilder parsedJdbcUrl = new StringBuilder("jdbc:mysql://")
                        .append(host).append(":").append(port).append(path)
                        .append("?allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");

                String query = uri.getQuery();
                if (StringUtils.hasText(query)) {
                    String lowerQuery = query.toLowerCase();
                    if (lowerQuery.contains("ssl-mode=required") || lowerQuery.contains("sslmode=required")) {
                        parsedJdbcUrl.append("&sslMode=REQUIRED&useSSL=true");
                    } else if (lowerQuery.contains("ssl-mode=disabled") || lowerQuery.contains("usessl=false")) {
                        parsedJdbcUrl.append("&useSSL=false");
                    } else {
                        parsedJdbcUrl.append("&").append(query);
                    }
                } else {
                    parsedJdbcUrl.append("&useSSL=false");
                }

                jdbcUrl = parsedJdbcUrl.toString();
                logger.info("Auto-configured cloud database JDBC connection: jdbc:mysql://{}:{}{}", host, port, path);
            } catch (Exception e) {
                logger.warn("Could not parse cloud database URI '{}', falling back to raw url: {}", rawUrl, e.getMessage());
            }
        }

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(effectiveUser);
        config.setPassword(effectivePass);

        logger.info("Initializing DataSource for URL: {} with user: {}", jdbcUrl.replaceAll(":[^/@]+@", ":****@"), effectiveUser);
        return new HikariDataSource(config);
    }
}
