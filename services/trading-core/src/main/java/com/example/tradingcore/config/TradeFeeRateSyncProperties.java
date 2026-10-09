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
 * <p><b>Порог свежести — калибровка НАБЛЮДЕНИЯ</b>
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
     * Размер страницы обхода контура у детектора несвежести — инструментов
     * площадки за одно чтение проекции и одну пачку навесов правил.
     *
     * <p><b>Предела выборки он не задаёт:</b> детектор проходит страницы до
     * последней, и проверку получает каждый инструмент каталога. Прежнее
     * окно отрезало хвост каталога по ключу — то есть одни и те же
     * инструменты не проверялись НИКОГДА, а не «в этом тике».
     */
    private Integer contourPageSize = 500;
}
