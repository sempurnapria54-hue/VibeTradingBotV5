package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.18} — пустая идентичность события у класса зерна
 * происшествий отвергается (.claude/tests/cases/statistics.md).
 *
 * <p><b>Предикат полноты у зерна происшествий СВОЙ</b>
 * ({@code IncidentFact.hasCompleteInput}), и снимается он отдельно от
 * сделочного: клетка {@code B2.9} его не видит вовсе. Пустая идентичность
 * схлопнула бы о свою отметку обработанного всякое следующее происшествие с
 * тем же пустым значением (docs/models/domain/other/StatisticsFact.md
 * §«Отметка обработанного»).
 *
 * <p>Класс записи — подъём ступени, несомый класс этого зерна. Свой класс
 * теста — потому, что клетка травит приём ({@link PoisonedReceptionBox}).
 */
class EmptyIncidentEventIdBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-18");
    }

    @Test
    @DisplayName("B2.18 — Пустая идентичность события у класса зерна происшествий отвергается")
    void anEmptyEventIdentityOfAnIncidentClassIsRejected() {
        givenReceptionStateRows();

        poisonUntilSettled(envelope("", HOLD_RAISED, occurredAt), TENANT,
                Bodies.holdRaised(ACCOUNT, Bodies.HARD, Bodies.MANUAL_HALT_REQUESTED));

        assertReceptionHalted();
    }
}
