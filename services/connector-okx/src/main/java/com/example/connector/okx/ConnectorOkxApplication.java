package com.example.connector.okx;

import com.example.connector.okx.config.CredentialsProperties;
import com.example.connector.okx.config.EnvironmentProperties;
import com.example.connector.okx.config.OkxProperties;
import com.example.platform.exception.handler.AccessDenialHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Точка входа сервиса {@code connector-okx}.
 *
 * <p><b>Ни JPA, ни Flyway, ни аудита здесь нет, и это не упущение:</b>
 * своей базы у коннектора нет по построению
 * ({@code docs/architecture/services.md} §«Что коннектор не знает»).
 *
 * <p>Энфорсер отказа доступа берётся из общего артефакта именованно: его
 * пакет вне области сканирования. Писателя следа отказа у коннектора нет —
 * базы нет, и точка входа отвечает отказом, ничего не записывая.
 */
@SpringBootApplication
@Import(AccessDenialHandler.class)
@EnableConfigurationProperties({OkxProperties.class, EnvironmentProperties.class, CredentialsProperties.class})
public class ConnectorOkxApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectorOkxApplication.class, args);
    }
}
