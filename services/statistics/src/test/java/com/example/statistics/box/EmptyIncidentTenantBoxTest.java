package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.19} — пустой ключ записи у класса зерна происшествий
 * отвергается (.claude/tests/cases/statistics.md).
 *
 * <p>Тенант — ключ и этого зерна (docs/models/domain/other/StatisticsFact.md
 * §«Факт происшествия»), и охраняет его свой предикат полноты
 * ({@code IncidentFact.hasCompleteInput}), не сделочный: клетка
 * {@code B2.16} его не видит. Свой класс — потому, что клетка травит приём
 * ({@link PoisonedReceptionBox}).
 */
class EmptyIncidentTenantBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-19");
    }

    @Test
    @DisplayName("B2.19 — Пустой ключ записи у класса зерна происшествий отвергается")
    void anEmptyRecordKeyOfAnIncidentClassIsRejected() {
        givenReceptionStateRows();

        poisonUntilSettled(envelope(POISON_EVENT, HOLD_RAISED, occurredAt), "",
                Bodies.holdRaised(ACCOUNT, Bodies.HARD, Bodies.MANUAL_HALT_REQUESTED));

        assertReceptionHalted();
    }
}
