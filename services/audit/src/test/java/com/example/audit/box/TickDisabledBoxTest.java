package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B3.7} — снятый выключатель тика уносит ряды тем же ходом
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Выключатель — ВХОД клетки</b>, поэтому контекст у неё свой: его
 * читает сам тик при каждом такте, а положение оси приезжает при подъёме.
 *
 * <p><b>Предмет здесь — ТИК, а не свидетельство приёма.</b> Соседняя
 * клетка {@code B2.14} стои́т на том же положении оси и спрашивает другое:
 * куда ложится флаг остановки, когда строки пары нет. Эта спрашивает, что
 * при снятом выключателе не заводится ни строки, ни ряда — и что приём при
 * этом ИДЁТ: строки журнала пишутся, а величины приёма писать некуда.
 *
 * <p><b>Обе величины полноты обязаны сказать одно и то же:</b> границы нет
 * и непрерывность не утверждаема — область квантора пуста
 * (docs/spec/durable-reception.json, {@code pairsObserved}).
 */
class TickDisabledBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b3-7";

    /** Возраст события, которым клетка предъявляет живой приём. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                Map.of(AuditSubstrate.STATE_TICK_ENABLED_KEY, "false"));
    }

    @Test
    @DisplayName("B3.7 — Снятый выключатель тика уносит ряды тем же ходом")
    void aDisabledTickCarriesTheSeriesAwayByTheSameMove() {
        assertThat(rows.count(RECEPTION_TABLE)).isZero();

        tick();

        assertThat(rows.count(RECEPTION_TABLE))
                .as("при снятом выключателе строк состояния не заводит никто").isZero();
        assertThat(receptionRowCount()).as("рядов приёма в выдаче нет").isZero();

        publish(topic(), "E-B3-7", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(topic());

        assertThat(records()).as("приём событий при этом идёт").hasSize(1);
        assertThat(rows.count(RECEPTION_TABLE))
                .as("а величины приёма писать некуда: они пишутся обновлением строки").isZero();
        assertThat(receptionRowCount()).isZero();
        assertThat(lowerBound()).as("границы нет, и это значение").isNull();
        assertThat(continuityClaimable()).as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
    }

    /** Тема, в которую клетка кладёт годную запись. */
    private String topic() {
        return subscription().getFirst();
    }
}
