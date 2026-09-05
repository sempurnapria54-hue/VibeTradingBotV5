package com.example.connector.okx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Окружение, в котором запущен процесс.
 *
 * <p>Коннектору оно нужно ровно за одним: имя окружения — первый сегмент
 * пути ключей в хранилище
 * ({@code com.example.tradingbot.domain.util.ExchangeAccountKeyPath}), и
 * именно оно делает границу между окружениями выразимой одной политикой
 * Vault ({@code docs/architecture/platform.md} §Безопасность).
 *
 * <p><b>Пустое значение — отказ, а не умолчание.</b> Процесс, не знающий
 * своего окружения, вычислил бы путь без первого сегмента и полез бы за
 * ключами не туда; лучше отказать на первом же вызове.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "platform.environment")
public class EnvironmentProperties {

    /** Имя окружения: {@code dev}, {@code stage}, {@code prod}. */
    private String name;
}
