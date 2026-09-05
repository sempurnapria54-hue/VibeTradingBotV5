package com.example.connector.okx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация связи с площадкой OKX.
 *
 * <p><b>Кредов здесь нет, и это главное отличие от донорской формы.</b> В
 * доноре ключи были свойствами процесса: счёт был один, и конфигурация
 * могла его описывать. У коннектора счёт — операнд вызова, а ключи
 * резолвятся по нему из хранилища
 * ({@code com.example.connector.okx.credentials.ExchangeCredentialsResolver}).
 * Оставь ключи в конфигурации — и стейтлесс-коннектор снова обслуживал бы
 * ровно один счёт, а второй тенант подписывался бы чужими ключами.
 *
 * <p><b>Демо-флага здесь тоже нет.</b> Контур — атрибут СЧЁТА, а не
 * процесса ({@code docs/architecture/platform.md} §«Контур площадки —
 * атрибут счёта, допустимость — атрибут окружения»): он приезжает вместе
 * с ключами и выражается заголовком на конкретном запросе.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okx")
public class OkxProperties {

    /** Базовый REST URL площадки (например, {@code https://www.okx.com}). */
    private String baseUrl;
}
