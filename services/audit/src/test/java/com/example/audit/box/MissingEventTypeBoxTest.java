package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.3} — отсутствует класс события
 * (.claude/tests/cases/audit.md).
 *
 * <p>Без дискриминатора содержимое не разбирается ничем
 * (docs/architecture/contracts.md §«Конверт события»), и строка журнала
 * несла бы факт, о котором нельзя сказать, чей он.
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class MissingEventTypeBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-3", Map.of());
    }

    @Test
    @DisplayName("B2.3 — Отсутствует класс события")
    void aMessageWithoutTheEventClassStopsReception() {
        givenReceptionStateRows();

        poisonWithout(EVENT_TYPE);

        assertReceptionHalted();
    }
}
