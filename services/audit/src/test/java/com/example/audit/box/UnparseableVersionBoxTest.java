package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.8} — версия формы не разбирается
 * (.claude/tests/cases/audit.md).
 *
 * <p>Довод тот же, что у момента происшествия: нечисловая версия не
 * подменяется пустотой — строка объявила бы, что версии не было.
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class UnparseableVersionBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-8", Map.of());
    }

    @Test
    @DisplayName("B2.8 — Версия формы не разбирается")
    void anUnparseableFormVersionStopsReception() {
        givenReceptionStateRows();

        poison(envelopeWith(VERSION, "первая"), TENANT, Bodies.reference());

        assertReceptionHalted();
    }
}
