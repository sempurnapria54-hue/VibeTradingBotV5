package com.example.connector.okx;

import com.example.connector.okx.config.CredentialsProperties;
import com.example.connector.okx.config.EnvironmentProperties;
import com.example.connector.okx.config.OkxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Точка входа сервиса {@code connector-okx}.
 *
 * <p><b>Ни JPA, ни Flyway, ни аудита здесь нет, и это не упущение:</b>
 * своей базы у коннектора нет по построению
 * ({@code docs/architecture/services.md} §«Что коннектор не знает»).
 */
@SpringBootApplication
@EnableConfigurationProperties({OkxProperties.class, EnvironmentProperties.class, CredentialsProperties.class})
public class ConnectorOkxApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectorOkxApplication.class, args);
    }
}
