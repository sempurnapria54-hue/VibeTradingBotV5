package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.1} — отсутствует идентичность события
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Это ГОЛОВНАЯ клетка группы:</b> ветвь отказа у неполного входа
 * одна, и все прочие поводы ведут ровно сюда. Различает клетки только то,
 * какого значения конверта не хватило.
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class MissingEventIdBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-1", Map.of());
    }

    @Test
    @DisplayName("B2.1 — Отсутствует идентичность события")
    void aMessageWithoutTheEventIdentityStopsReception() {
        givenReceptionStateRows();

        poisonWithout(EVENT_ID);

        assertReceptionHalted();
    }
}
