package com.example.tradingcore.config;

import java.time.Duration;
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
     * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
     * одного поля»). Направление
     * калибровки названо: окно шире каталога — норма, у́же — молчаливый
     * пропуск входов по инструментам, до которых тик не дошёл.
     */
    private Integer instrumentWindow = 200;

    /**
     * Допустимый возраст последнего наблюдённого прохода проактивной
     * детекции: старше — либо наблюдения не было вовсе — счёт риска не
     * набирает (docs/components/EntryScannerJob.md §«Гейт входа»).
     *
     * <p><b>Число разведочное и выведено из такта детекции</b>, а не из
     * риск-аппетита: три такта — принятый предел слепоты
     * ({@code anomaly-job.blind-pass-limit}) — плюс четверть такта, чтобы
     * порог не совпал ни с одним возрастом, который дают сдвиг расписаний
     * отбора и детекции на ноль либо полтакта. Направление названо: ложный
     * отказ снимается сам ближайшим наблюдённым проходом, а пропуск — вход
     * вслепую. Дом числа и условие выхода из разведочного режима —
     * docs/components/AnomalyJob.md §«Гейт полноты среза».
     */
    private Duration observationMaxAge = Duration.ofSeconds(195);
}
