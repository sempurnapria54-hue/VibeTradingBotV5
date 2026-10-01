package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Кумулятивный ряд через сдвиг окна расчёта — клетка {@code B4.13}
 * документа `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Свой контекст, потому что окно расчёта есть ось конфигурации.</b>
 * Штатное окно в полторы тысячи баров сдвига на ряде кейса не даёт вовсе:
 * ряд целиком лежит в окне, и сумма, начатая заново каждым проходом,
 * совпадала бы с продолженной. Окно в пять баров при шести барах ряда
 * сдвигается на бар с первой же новой свечой.
 *
 * <p><b>Ряд растёт на единицу закрытия с равным объёмом</b>
 * ({@link Feed#candles}): знаковый объём каждого бара равен объёму
 * {@link #BAR_VOLUME}, в том числе у бара, выпадающего из окна при сдвиге.
 * Поэтому сумма, начатая заново вторым проходом, дала бы на новом баре то же
 * значение, что первый проход на предыдущем, — а продолженная даёт его плюс
 * объём нового бара (docs/components/IndicatorJob.md §«Кумулятивный тип
 * продолжает записанный ряд»).
 */
class CandleWindowBoxTest extends MarketDataBox {

    /** Окно расчёта производных в барах — ось конфигурации клетки. */
    private static final String CANDLE_WINDOW_BARS = "5";

    /** Объём каждого бара ряда {@link Feed#candles}. */
    private static final BigDecimal BAR_VOLUME = new BigDecimal("10");

    /** Баров в ряду до первого тика расчёта: на один больше окна. */
    private static final Integer FIRST_SERIES_BARS = 6;

    /** Открытие первого бара ряда, в барах назад от момента прогона. */
    private static final Integer FIRST_BAR_BACK = 7;

    private static final Integer BASE_PRICE = 50000;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("market-data.candle-window-bars", CANDLE_WINDOW_BARS);
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B4.13 — кумулятивный ряд продолжается через сдвиг окна")
    void b4_13_theCumulativeSeriesContinuesThroughAWindowShift() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        // Горизонт мельче ряда: бэкфилл достигает его первой же страницей и
        // уводит группу в проверку, откуда расчёт её уже берёт.
        requireCandles(instrument, HOUR, 5L);
        connector.answers(ConnectorStub.HISTORY_CANDLES, Feed.candles(
                barsAgo(HOUR_MILLIS, FIRST_BAR_BACK), HOUR_MILLIS, FIRST_SERIES_BARS, BASE_PRICE));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        requireIndicator("OBV", HOUR, "{\"enabled\": true}");
        tick(Tick.INDICATORS);
        List<BigDecimal> firstTick = storedObv();
        assertThat(firstTick)
                .as("предусловие: первый тик записал значения на барах окна после первого")
                .hasSize(FIRST_SERIES_BARS - 2);
        // Хвост площадки перекрывает ряд и несёт одну новую закрытую свечу:
        // закрытие выше предыдущего, объём — BAR_VOLUME.
        connector.answers(ConnectorStub.CANDLES, Feed.candles(
                barsAgo(HOUR_MILLIS, FIRST_BAR_BACK), HOUR_MILLIS, FIRST_SERIES_BARS + 1, BASE_PRICE));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        assertThat(rows.count("candles"))
                .as("предусловие: новая свеча легла в ряд")
                .isEqualTo(Long.valueOf(FIRST_SERIES_BARS + 1));

        tick(Tick.INDICATORS);

        List<BigDecimal> secondTick = storedObv();
        assertThat(secondTick)
                .as("записано ровно одно новое значение — на новом баре")
                .hasSize(firstTick.size() + 1);
        assertThat(secondTick.subList(0, firstTick.size()))
                .as("значения первого тика не переписаны")
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyElementsOf(firstTick);
        assertThat(secondTick.getLast())
                .as("значение первого тика на предыдущем баре плюс объём нового; выпавший бар не вычтен")
                .isEqualByComparingTo(firstTick.getLast().add(BAR_VOLUME));
    }

    /** Записанные значения OBV в порядке баров. */
    private List<BigDecimal> storedObv() {
        return rows.allOrderedBy("indicator_values", "candle_timestamp").stream()
                .map(row -> (BigDecimal) row.get("obv"))
                .toList();
    }
}
