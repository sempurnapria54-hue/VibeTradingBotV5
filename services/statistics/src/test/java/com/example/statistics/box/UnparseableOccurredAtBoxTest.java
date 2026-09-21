package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.6} — момент происшествия не разбирается
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Подстановки момента приёма на его место не происходит</b>
 * ({@code EnvelopeReader.moment}): подставленный, он выдал бы момент НАШЕГО
 * чтения за момент происшествия — то есть сообщил бы о шкале, которой у факта
 * нет (docs/rules/time-utc.md).
 *
 * <p>Исход общий всей группе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}).
 */
class UnparseableOccurredAtBoxTest extends PoisonedReceptionBox {

    /** Значение заголовка, моментом по ISO-8601 не являющееся. */
    private static final String NOT_A_MOMENT = "вчера";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-6");
    }

    @Test
    @DisplayName("B2.6 — Момент происшествия не разбирается")
    void anUnparseableOccurrenceMomentStopsReception() {
        givenReceptionStateRows();

        poison(envelopeWith(OCCURRED_AT, NOT_A_MOMENT), TENANT,
                Bodies.dealClosed(ACCOUNT, "S-1"));

        assertReceptionHalted();
    }
}
