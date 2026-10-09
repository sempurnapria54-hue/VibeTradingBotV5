package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.smokelive.Perimeter.Reply;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.smokelive.SmokeRun.decimal;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E3} дыма: рыночные данные площадки и их контур
 * (.claude/tests/cases/smoke-live.md §«E3 — Рыночные данные площадки и их
 * контур»).
 *
 * <p><b>Кода нет у двух кейсов, и причины разные.</b> {@code E3.3} сверяет
 * перечень с демо-контуром площадки, а публичного чтения в закрытом перечне
 * канала прогона нет ({@link OkxReadChannel}); {@code E3.4} недостижим —
 * возраст ряда ставится только ожиданием (.claude/tests/cases/smoke-live.md
 * §«Кейсы, не прогоняемые сегодня»).
 *
 * <p><b>Не ассертится — наружу не наблюдается</b> ({@code G6}): «к
 * коннектору ушло публичное чтение под служебной идентичностью» и «подписанного
 * обращения на этом ходу не было». Значения индикаторов {@code E3.2} поверхность
 * отдаёт только по идентичности настройки индикатора, которой снаружи взять
 * негде, — их половина ожидания кодом не покрыта.
 */
@Tag("smoke")
@DisplayName("E3 — Рыночные данные площадки и их контур")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E3MarketDataSmokeTest {

    private static final List<String> LISTED = List.of("SYNC", "CANDLES_LOADING", "ACTIVE");

    private static final Map<String, Duration> TIMEFRAMES = Map.of(
            "ONE_MINUTE", Duration.ofMinutes(1), "THREE_MINUTES", Duration.ofMinutes(3),
            "FIVE_MINUTES", Duration.ofMinutes(5), "FIFTEEN_MINUTES", Duration.ofMinutes(15),
            "ONE_HOUR", Duration.ofHours(1), "TWO_HOURS", Duration.ofHours(2),
            "FOUR_HOURS", Duration.ofHours(4), "ONE_DAY", Duration.ofDays(1));

    /** Баров на окно чтения ряда. */
    private static final Integer WINDOW_BARS = 30;

    /**
     * Предел возраста последнего бара — два бара своего таймфрейма плюс запас
     * на такт сбора. Срок свежести задаёт стратегия
     * (docs/rules/market-data-freshness.md), а у определения дыма индикаторов
     * нет; поэтому предел взят от таймфрейма: последний закрытый бар открылся
     * не раньше двух баров назад.
     */
    private static final Duration COLLECTION_SLACK = Duration.ofMinutes(5);

    private static SmokeRun run;

    @BeforeAll
    static void requirePreconditions() {
        run = SmokeRun.get();
        run.requireCommonPreconditions();
    }

    @Test
    @Order(1)
    @DisplayName("E3.1 — Синк инструментов даёт торгуемый инструмент с биржевыми правилами")
    void e3_1_theInstrumentSyncYieldsATradableInstrumentWithRules() {
        List<String> dealsBefore = run.dealSignatures();

        Reply sync = run.perimeter().post("/api/v1/market-data/jobs/instrument-sync", null);
        run.awaitTrace("инструмент дыма в каталоге после синка", run.exchangeTimeout(),
                () -> nonNull(run.findInstrument()));
        JsonNode listed = run.findInstrument();
        Reply rules = run.perimeter().get("/api/v1/market-data/instruments/" + run.instrument() + "/rules");

        assertThat(sync.status()).as("E3.1: тик синка инструментов не принят: %s", sync).isEqualTo(202);
        assertThat(listed.path("status").asString("")).as("E3.1: инструмент дыма в каталоге не торгуем: %s", listed)
                .isIn(LISTED);
        assertThat(rules.status()).as("E3.1: правила инструмента дыма не отданы: %s", rules).isEqualTo(200);
        assertThat(rules.json().path("status").asString("")).as("E3.1: инструмент на площадке не LIVE: %s", rules)
                .isEqualTo("LIVE");
        for (String field : List.of("externalMinSize", "externalLotSize", "externalTickSize")) {
            assertThat(rules.json().path(field).asString("")).as("E3.1: правило %s пусто: %s", field, rules)
                    .isNotBlank();
            assertThat(decimal(rules.json().path(field))).as("E3.1: правило %s не положительно", field)
                    .isPositive();
        }
        assertThat(run.dealSignatures()).as("E3.1: синк инструментов оставил след в сделках счёта")
                .isEqualTo(dealsBefore);
    }

    @Test
    @Order(2)
    @DisplayName("E3.2 — Ряды инструмента собираются, и их возраст в пределах свежести")
    void e3_2_theInstrumentSeriesAreCollectedAndFresh() {
        run.perimeter().post("/api/v1/trading-core/jobs/strategy-demand", null);
        Reply candles = run.perimeter().post("/api/v1/market-data/jobs/candles", null);
        Reply indicators = run.perimeter().post("/api/v1/market-data/jobs/indicators", null);
        Reply groups = run.perimeter().get("/api/v1/market-data/instruments/" + run.instrument() + "/candle-groups");

        assertThat(candles.status()).as("E3.2: тик свечей не принят: %s", candles).isEqualTo(202);
        assertThat(indicators.status()).as("E3.2: тик индикаторов не принят: %s", indicators).isEqualTo(202);
        assertThat(groups.status()).as("E3.2: группы свечей не прочитаны: %s", groups).isEqualTo(200);
        List<JsonNode> collected = new ArrayList<>();
        groups.json().forEach(group -> {
            if (TIMEFRAMES.containsKey(group.path("timeframe").asString(""))
                    && "ACTIVE".equals(group.path("status").asString(""))) {
                collected.add(group);
            }
        });
        assertThat(collected).as("E3.2: у инструмента дыма нет ни одной собранной группы свечей — требований"
                + " по нему никто не заявил (предусловие: заявлены ядром): %s", groups).isNotEmpty();
        BigDecimal lastClose = null;
        for (JsonNode group : collected) {
            String timeframe = group.path("timeframe").asString();
            Duration bar = TIMEFRAMES.get(timeframe);
            Long from = Instant.now().minus(bar.multipliedBy(WINDOW_BARS)).toEpochMilli();
            Reply series = run.perimeter().get("/api/v1/market-data/instruments/" + run.instrument()
                    + "/candles?timeframe=" + timeframe + "&fromMillis=" + from + "&limit=" + (WINDOW_BARS + 1));
            assertThat(series.status()).as("E3.2: ряд %s не прочитан: %s", timeframe, series).isEqualTo(200);
            List<Long> opens = new ArrayList<>();
            series.json().forEach(candle -> opens.add(candle.path("openTimestamp").asLong()));
            assertThat(opens).as("E3.2: ряд %s пуст", timeframe).isNotEmpty();
            for (int index = 1; index < opens.size(); index++) {
                assertThat(opens.get(index) - opens.get(index - 1))
                        .as("E3.2: разрыв в ряду %s между барами %s и %s", timeframe, opens.get(index - 1),
                                opens.get(index))
                        .isEqualTo(bar.toMillis());
            }
            Instant lastOpen = Instant.ofEpochMilli(opens.getLast());
            assertThat(lastOpen).as("E3.2: последний бар ряда %s старше двух баров и такта сбора", timeframe)
                    .isAfter(Instant.now().minus(bar.multipliedBy(2)).minus(COLLECTION_SLACK));
            lastClose = decimal(series.json().get(series.json().size() - 1).path("close"));
        }
        Reply prices = run.perimeter().get("/api/v1/market-data/instruments/" + run.instrument() + "/prices");
        assertThat(prices.status()).as("E3.2: цены момента не отданы: %s", prices).isEqualTo(200);
        BigDecimal last = decimal(prices.json().path("externalLastPrice"));
        BigDecimal tick = decimal(run.perimeter().get("/api/v1/market-data/instruments/" + run.instrument()
                + "/rules").json().path("externalTickSize"));
        assertThat(last.remainder(tick).signum()).as("E3.2: цена %s не кратна шагу цены %s — вычислена, а не"
                + " пришла с площадки", last, tick).isZero();
        assertThat(last).as("E3.2: цена момента совпала с закрытием последнего бара — подставлена, а не"
                + " наблюдена").isNotEqualByComparingTo(lastClose);
    }
}
