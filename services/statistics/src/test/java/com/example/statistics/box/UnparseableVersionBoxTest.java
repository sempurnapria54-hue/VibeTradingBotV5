package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.7} — версия формы не разбирается
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Версия не подменяется ни нулём, ни умолчанием</b>
 * ({@code EnvelopeReader.version}): она говорит, по какой форме читать
 * содержимое (docs/architecture/contracts.md §«Конверт события»), и
 * подставленная выдала бы догадку за объявление производителя.
 *
 * <p>Исход общий всей группе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}).
 */
class UnparseableVersionBoxTest extends PoisonedReceptionBox {

    /** Значение заголовка, десятичным числом не являющееся. */
    private static final String NOT_A_NUMBER = "v1";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-7");
    }

    @Test
    @DisplayName("B2.7 — Версия формы не разбирается")
    void anUnparseableFormVersionStopsReception() {
        givenReceptionStateRows();

        poison(envelopeWith(VERSION, NOT_A_NUMBER), TENANT, Bodies.dealClosed(ACCOUNT, "S-1"));

        assertReceptionHalted();
    }
}
