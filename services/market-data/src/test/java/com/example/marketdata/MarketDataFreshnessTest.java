package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.marketdata.domain.service.IndicatorService;
import com.example.marketdata.domain.service.MarketDataExpirationChecker;
import com.example.marketdata.persistence.service.IndicatorDataService;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Толерантность приносит ЧИТАТЕЛЬ, а не строка результата.
 *
 * <p>Это и есть смысл смены ключевания: одно и то же значение шарится
 * между заказчиками, и годным оно оказывается для одного и негодным для
 * другого (docs/rules/market-data-freshness.md,
 * docs/models/domain/other/IndicatorValue.md §«Ключевание — идентичностью
 * вычисления»). Тест проверяет именно это: строка одна, срок разный,
 * исход разный.
 */
class MarketDataFreshnessTest {

    private static final Long INSTRUMENT_ID = 1L;
    private static final Long CONFIG_ID = 7L;

    private final IndicatorDataService dataService = mock(IndicatorDataService.class);
    private final IndicatorService indicatorService =
            new IndicatorService(dataService, new MarketDataExpirationChecker());

    /** Одно значение, два читателя с разной толерантностью — два разных исхода. */
    @Test
    void toleranceBelongsToTheReader() {
        when(dataService.findLatest(INSTRUMENT_ID, CONFIG_ID))
                .thenReturn(Optional.of(atrAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10))));

        assertThat(indicatorService.getLatestValue(INSTRUMENT_ID, CONFIG_ID, Duration.ofHours(1))).isPresent();
        assertThat(indicatorService.getLatestValue(INSTRUMENT_ID, CONFIG_ID, Duration.ofMinutes(5))).isEmpty();
    }

    /**
     * Пустой срок — не «бессрочно свежо», а отказ.
     *
     * <p>Читатель, не назвавший толерантности, не сказал, чему он готов
     * доверять; трактовать молчание как «доверяю любому возрасту» значило
     * бы ошибиться в разрешающую сторону.
     */
    @Test
    void absentToleranceIsRefusal() {
        MarketDataExpirationChecker checker = new MarketDataExpirationChecker();

        assertThat(checker.isFresh(OffsetDateTime.now(ZoneOffset.UTC), null)).isFalse();
        assertThat(checker.isFresh(null, Duration.ofHours(1))).isFalse();
    }

    /** Предыдущее значение свежестью не гейтится: это направление, а не точка решения. */
    @Test
    void previousValueIsNotFreshnessGated() {
        IndicatorValue latest = atrAt(OffsetDateTime.now(ZoneOffset.UTC).minusYears(1));
        IndicatorValue previous = atrAt(OffsetDateTime.now(ZoneOffset.UTC).minusYears(1).minusHours(1));
        when(dataService.findLatestTwo(INSTRUMENT_ID, CONFIG_ID)).thenReturn(java.util.List.of(latest, previous));

        assertThat(indicatorService.getPreviousValue(INSTRUMENT_ID, CONFIG_ID)).contains(previous);
    }

    private IndicatorValue atrAt(OffsetDateTime candleTimestamp) {
        AtrValue value = new AtrValue();
        value.setInstrumentId(INSTRUMENT_ID);
        value.setIndicatorConfigId(CONFIG_ID);
        value.setCandleTimestamp(candleTimestamp);
        value.setAtr(BigDecimal.ONE);
        return value;
    }
}
