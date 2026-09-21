package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B3.7} — снятый выключатель тика уносит ряды тем же ходом
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Выключатель — ВХОД клетки</b>, поэтому контекст у неё свой: его
 * читает сам тик при каждом такте, а положение оси приезжает при подъёме.
 *
 * <p><b>Предмет здесь — ТИК, а не свидетельство приёма.</b> Клетка
 * спрашивает, что при снятом выключателе не заводится ни строки, ни ряда — и
 * что приём при этом ИДЁТ: строка факта появляется, а величины приёма писать
 * некуда, потому что пишутся они обновлением существующей строки.
 *
 * <p><b>Обе величины полноты обязаны сказать одно и то же:</b> границы нет и
 * непрерывность не утверждаема — область квантора пуста
 * (docs/spec/durable-reception.json, {@code pairsObserved}).
 */
class TickDisabledBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её тема. */
    private static final String SLUG = "b3-7";

    /** Биржевой счёт — обязательный ключ обоих зёрен. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — компонент ключа сделочного зерна. */
    private static final String STRATEGY = "S-1";

    /** Возраст события, которым клетка предъявляет живой приём. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG,
                Map.of(StatisticsSubstrate.STATE_TICK_ENABLED_KEY, "false"));
    }

    @Test
    @DisplayName("B3.7 — Снятый выключатель тика уносит ряды тем же ходом")
    void aDisabledTickCarriesTheSeriesAwayByTheSameMove() {
        assertThat(rows.count(RECEPTION_TABLE)).isZero();

        tick();

        assertThat(rows.count(RECEPTION_TABLE))
                .as("при снятом выключателе строк состояния не заводит никто").isZero();
        assertThat(receptionRowCount()).as("рядов приёма в выдаче нет").isZero();
        assertThat(get(METRICS_SCRAPE, TENANT).status())
                .as("сам эндпоинт при этом отвечает").isEqualTo(200);

        publish("E-B3-7", DEAL_CLOSED, momentsAgo(EVENT_AGE), Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();

        assertThat(dealFacts()).as("приём событий при этом идёт").hasSize(1);
        assertThat(rows.count(RECEPTION_TABLE))
                .as("а величины приёма писать некуда: они пишутся обновлением строки").isZero();
        assertThat(receptionRowCount()).isZero();
        assertThat(lowerBound()).as("границы нет, и это значение").isNull();
        assertThat(continuityClaimable())
                .as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
    }
}
