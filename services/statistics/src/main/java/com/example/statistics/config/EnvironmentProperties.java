package com.example.statistics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Оси окружения, доезжающие до сервиса ключами манифеста
 * (docs/architecture/platform.md §«Чем различаются окружения»).
 *
 * <p><b>Ось у сервиса одна — имя окружения.</b> Профиля хранения здесь нет
 * и не будет: глубины у фактов нет ни в одном окружении, а ось
 * {@code journalRetentionProfile} объявлена у журнала и его предмета не
 * покидает (docs/models/domain/other/StatisticsFact.md §Персистентность).
 *
 * <p>Пустое имя означает, что ось не доехала, — это отказ, а не умолчание.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "platform.environment")
public class EnvironmentProperties {

    /** Имя окружения: `dev`, `stage`, `prod`. */
    private String name;
}
