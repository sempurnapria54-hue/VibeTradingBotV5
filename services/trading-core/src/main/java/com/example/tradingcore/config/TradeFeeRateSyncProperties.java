package com.example.tradingcore.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки тика синка ставок комиссии.
 *
 * <p>Выключатель и CRON — требование конвенции джоб
 * (.claude/rules/codestyle.md §Джобы): период тика задаётся
 * конфигурацией, а не хардкодом, и при выключенном флаге не делают
 * ничего ни запланированный тик, ни ручной.
 *
 * <p><b>Порог свежести и окно контура — калибровка НАБЛЮДЕНИЯ</b>
 * (.claude/processes/question-delegation.md §«Число: риск-аппетит против
 * калибровки наблюдения»): ошибка порога меряется парой «ложный запрет
 * входов против сайзинга по устаревшей ставке», а не размером потери.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "trade-fee-rate-sync")
public class TradeFeeRateSyncProperties {

    /** Тик включён. */
    private Boolean enabled;

    /** Расписание тика. */
    private String cron;

    /**
     * Порог свежести ставки: строка группы, не подтверждённая источником
     * дольше него, несвежа (docs/rules/instrument-hold.md §«Несвежесть
     * ставки комиссии»).
     *
     * <p><b>Порог не кратен такту намеренно.</b> При кратном исход
     * решали бы секунды дрейфа расписания: возраст на N-м пропущенном такте
     * равен порогу с точностью до них.
     */
    private Duration freshnessThreshold = Duration.ofHours(27);

    /**
     * Окно выборки контура — инструментов площадки, чью ставку проверяет
     * детектор несвежести за один тик. Упор в окно пишется в лог:
     * инструменты за его краем в этом тике не проверены.
     */
    private Integer contourWindow = 500;
}
