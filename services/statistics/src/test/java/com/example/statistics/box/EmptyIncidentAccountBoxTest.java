package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.20} — пустой биржевой счёт у класса зерна происшествий
 * отвергается (.claude/tests/cases/statistics.md).
 *
 * <p>Счёт — ключ зерна происшествий (docs/models/domain/other/StatisticsFact.md
 * §«Факт происшествия»); исход тот же, что у отсутствующего счёта этого зерна
 * ({@link MissingIncidentAccountBoxTest}), но мерит клетка единицу, которую
 * отсутствующая форма не видит: пустую строку {@code not null} колонки
 * пропускает, и предикат полноты у неё единственный охранник. Свой класс —
 * потому, что клетка травит приём ({@link PoisonedReceptionBox}).
 */
class EmptyIncidentAccountBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-20");
    }

    @Test
    @DisplayName("B2.20 — Пустой биржевой счёт у класса зерна происшествий отвергается")
    void anEmptyExchangeAccountOfAnIncidentClassIsRejected() {
        givenReceptionStateRows();

        poisonUntilSettled(envelope(POISON_EVENT, HOLD_RAISED, occurredAt), TENANT,
                Bodies.holdRaised("", Bodies.HARD, Bodies.MANUAL_HALT_REQUESTED));

        assertReceptionHalted();
    }
}
