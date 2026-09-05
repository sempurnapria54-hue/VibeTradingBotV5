package com.example.marketdata.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация загрузки свечей (docs/processes/candle-loading.md):
 * выключатель, период тика, размер страницы и число попыток докачки дыр.
 *
 * <p><b>Списка таймфреймов здесь больше нет.</b> В монолите он был
 * конфигурацией, и группы заводились каждому инструменту на каждый
 * настроенный таймфрейм; теперь группу заводит требование потребителя, и
 * статический список сделал бы клейм «собираем то, что кому-то нужно»
 * ложным (docs/processes/candle-loading.md §«Кто заводит группу»).
 *
 * <p><b>Планового горизонта по умолчанию тоже нет:</b> глубину называет
 * требование, а умолчание молча собирало бы историю, которую никто не
 * заказывал.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "candle-loading")
public class CandleLoadingProperties {

    /** Выключатель джобы: при false запланированный и ручной тик ничего не делают. */
    private Boolean enabled = true;

    /** Лимит свечей в одном запросе к площадке. */
    private Integer pageSize = 100;

    /** Число попыток докачки дыр до перевода группы в ERROR. */
    private Integer maxRepairAttempts = 5;
}
