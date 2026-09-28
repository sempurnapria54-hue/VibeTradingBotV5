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
 * <p><b>Окно наблюдения — операнд дедупа факта-СОСТОЯНИЯ.</b> Держание
 * состояния читается по времени создания стоящей строки, а по истечении
 * окна наблюдение записывается заново. Подтверждение гистерезиса окном не
 * читается — его носитель серия последнего наблюдения на той же строке
 * (docs/components/AnomalyJob.md §«Такт и гистерезис»); окно лишь
 * ограничивает, какая строка стои́т. Величина калибровочная и живёт только
 * в конфигурации: в двух носителях такие числа не хранятся.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "anomaly-report")
public class AnomalyReportProperties {

    /** Окно, в котором стоящая строка читается держащимся состоянием. */
    private Duration observationWindow = Duration.ofHours(1);
}
