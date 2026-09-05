package com.example.connector.okx.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройка кэша ключей счетов.
 *
 * <p>Срок жизни записи задан конфигурацией, а не константой в коде,
 * потому что он есть компромисс между нагрузкой на хранилище и окном
 * устаревания при ротации ключей — а положение этого компромисса
 * различается по окружениям: в {@code dev} ротация ручная и частая, в
 * {@code prod} редкая и объявленная.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "credentials")
public class CredentialsProperties {

    /**
     * Срок жизни записи кэша ключей. Ноль либо отрицательное значение
     * выключают кэш: каждый вызов идёт в хранилище.
     */
    private Duration cacheTtl = Duration.ofSeconds(60);
}
