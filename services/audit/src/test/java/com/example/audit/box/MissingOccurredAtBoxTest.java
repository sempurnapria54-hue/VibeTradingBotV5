package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.4} — отсутствует момент происшествия
 * (.claude/tests/cases/audit.md).
 *
 * <p>Обе оси времени строки несущие: момент происшествия задаёт окно
 * чтения, момент приёма — границу полноты
 * (docs/models/domain/other/AuditRecord.md §«Когда строка не пишется»).
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class MissingOccurredAtBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-4", Map.of());
    }

    @Test
    @DisplayName("B2.4 — Отсутствует момент происшествия")
    void aMessageWithoutTheOccurrenceMomentStopsReception() {
        givenReceptionStateRows();

        poisonWithout(OCCURRED_AT);

        assertReceptionHalted();
    }
}
