package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки отбора входа (docs/components/EntryScannerJob.md).
 *
 * <p>Период тика читает {@code @Scheduled} напрямую из
 * {@code entry-scanner.cron}; выключатель отдельным полем — конвенция
 * джоб (.claude/rules/codestyle.md §Джобы).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "entry-scanner")
public class EntryScannerProperties {

    /** Отбор входа включён. */
    private Boolean enabled = Boolean.TRUE;

    /**
     * Окно выборки торгуемых инструментов площадки за один тик.
     *
     * <p>Окно обязательно: каталог растёт вместе с контуром
     * (.claude/rules/codestyle.md §«Выборка данных»). Направление
     * калибровки названо: окно шире каталога — норма, у́же — молчаливый
     * пропуск входов по инструментам, до которых тик не дошёл.
     */
    private Integer instrumentWindow = 200;
}
