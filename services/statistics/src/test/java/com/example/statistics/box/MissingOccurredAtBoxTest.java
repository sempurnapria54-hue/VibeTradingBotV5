package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.2} — отсутствует момент происшествия
 * (.claude/tests/cases/statistics.md).
 *
 * <p>Момент происшествия двигает возраст последнего принятого события, и
 * пустой делает измерение произвольным: нижняя граница полноты выводится
 * именно из него (docs/rules/durable-consumer-reception.md §«Нижняя граница»).
 *
 * <p>Исход общий всей группе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}).
 */
class MissingOccurredAtBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-2");
    }

    @Test
    @DisplayName("B2.2 — Отсутствует момент происшествия")
    void aMessageWithoutTheOccurrenceMomentStopsReception() {
        givenReceptionStateRows();

        poisonWithout(OCCURRED_AT);

        assertReceptionHalted();
    }
}
