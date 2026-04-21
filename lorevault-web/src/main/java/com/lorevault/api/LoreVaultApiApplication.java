package com.lorevault.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
        // Spring AI auto-configuration disabled via manual bean creation
})
@ConfigurationPropertiesScan
@EnableScheduling
@OpenAPIDefinition(
    info = @Info(
        title = "LoreVault API",
        version = "0.8.3-SNAPSHOT",
        description = "Agentic Knowledge Ingestion Service for processing narrative content",
        contact = @Contact(name = "LoreVault Team")
    ),
    servers = {
        @Server(url = "http://localhost:18080", description = "Development server")
    }
)
public class LoreVaultApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoreVaultApiApplication.class, args);
    }
}
