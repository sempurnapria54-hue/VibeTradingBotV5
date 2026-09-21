package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.8}, вторая её запись — тело разбирается как документ, а
 * денежный операнд в нём записан нечисловой строкой
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Испорченный операнд НЕ подменяется пустотой</b>
 * ({@code EnvelopeReader.number}): подменённый, он выдал бы себя за «не
 * приехало» — то есть недобытое стало бы исходом (docs/concept.md, П1), а
 * пустота у денежного операнда имеет собственный смысл, который разбирает
 * свёртка пересчёта.
 *
 * <p>Довод разведения по классам — у соседа
 * ({@link UnparseableContentBoxTest}).
 */
class UnparseableNumberBoxTest extends PoisonedReceptionBox {

    /** Записи денежного операнда, числом не являющейся. */
    private static final String NOT_A_NUMBER = "двенадцать";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-8b");
    }

    @Test
    @DisplayName("B2.8 — Числовой операнд содержимого не разбирается как число")
    void anUnparseableNumericOperandStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt), TENANT,
                Bodies.dealClosedWithNonNumericResult(ACCOUNT, NOT_A_NUMBER));

        assertReceptionHalted();
    }
}
