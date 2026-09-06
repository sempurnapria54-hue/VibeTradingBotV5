package com.example.tradingcore.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки журнала происшествий
 * (docs/models/domain/other/AnomalyReport.md).
 *
 * <p><b>Окно наблюдения — операнд дедупа факта-СОСТОЯНИЯ.</b> Носителя
 * «последнее наблюдение» в модели нет, поэтому держание состояния читается
 * по времени создания стоящей строки, а по истечении окна наблюдение
 * записывается заново. Величина калибровочная и живёт только в
 * конфигурации: в двух носителях такие числа не хранятся.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "anomaly-report")
public class AnomalyReportProperties {

    /** Окно, в котором стоящая строка читается подтверждением держащегося состояния. */
    private Duration observationWindow = Duration.ofHours(1);
}
