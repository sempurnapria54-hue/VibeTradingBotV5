package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Оси окружения, которые сервис обязан знать
 * (docs/architecture/platform.md §«Чем различаются окружения»).
 *
 * <p>Значения приезжают из манифеста окружения, а не назначаются здесь:
 * перечень осей закрыт домом, и сервис его читает, а не переобъявляет.
 *
 * <p><b>Допустимых контуров здесь нет, и это не пропуск:</b> контур
 * проверяется в момент РЕГИСТРАЦИИ счёта, а регистрирует счета `auth`
 * (docs/architecture/platform.md §«Контур площадки — атрибут счёта»).
 * Ядро торгует по счетам, которые уже допущены.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "platform.environment")
public class EnvironmentProperties {

    /** Имя окружения — `dev`, `stage`, `prod`. */
    private String name;
}
