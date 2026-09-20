package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.7} — момент происшествия не разбирается
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Пустотой непустое неразбираемое значение не подменяется.</b>
 * Строка с пустым моментом происшествия несла бы ложное «значения не
 * было», и читатель окна принял бы потерю за отсутствие
 * (docs/rules/absent-value-semantics.md).
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class UnparseableOccurredAtBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-7", Map.of());
    }

    @Test
    @DisplayName("B2.7 — Момент происшествия не разбирается")
    void anUnparseableOccurrenceMomentStopsReception() {
        givenReceptionStateRows();

        poison(envelopeWith(OCCURRED_AT, "вчера"), TENANT, Bodies.reference());

        assertReceptionHalted();
    }
}
