package com.lorevault.catalog.internal;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableConfigurationProperties(CatalogDataSourceProperties.class)
@ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "true")
public class CatalogConfig {

    @Bean
    public DataSource catalogDataSource(CatalogDataSourceProperties props) {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(props.url())
                .username(props.username())
                .password(props.password())
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean
    public JdbcClient catalogJdbcClient(DataSource catalogDataSource) {
        return JdbcClient.create(new JdbcTemplate(catalogDataSource));
    }

    @Bean
    public PlatformTransactionManager catalogTransactionManager(DataSource catalogDataSource) {
        return new DataSourceTransactionManager(catalogDataSource);
    }

    @Bean(initMethod = "migrate")
    public Flyway catalogFlyway(DataSource catalogDataSource) {
        return Flyway.configure()
                .dataSource(catalogDataSource)
                .locations("classpath:db/migration/catalog")
                .load();
    }
}
