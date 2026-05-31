package com.example.tradingbot.config;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация загрузки свечей (docs/processes/candle-loading.md):
 * период тика, размер страницы, перекрытие SYNC, число попыток
 * REPAIR, плановый горизонт по умолчанию и набор таймфреймов. Числа
 * конфигурируемы, проставляются на CODE.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "candle-loading")
public class CandleLoadingProperties {

    /** CRON-период тика CandleJob. */
    private String cron;

    /** Лимит свечей в одном запросе OKX (≤ 100 для history-candles). */
    private int pageSize = 100;

    /** Перекрытие хвоста при SYNC (число последних баров перезапрашивается). */
    private int syncOverlapBars = 3;

    /** Число попыток REPAIR до перевода группы в ERROR. */
    private int maxRepairAttempts = 5;

    /** Дефолтный плановый горизонт истории (UTC мс) при онбординге. */
    private long defaultPlannedStartDate;

    /** Таймфреймы, под которые заводятся группы свечей инструмента. */
    private List<TimeFrame> timeframes = List.of(TimeFrame.ONE_HOUR);
}
