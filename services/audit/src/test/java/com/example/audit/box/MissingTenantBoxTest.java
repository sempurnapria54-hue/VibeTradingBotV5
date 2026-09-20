package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.2} — отсутствует тенант
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Тенант едет КЛЮЧОМ записи, а не заголовком</b>
 * (docs/architecture/contracts.md §«Как конверт лежит на проводе —
 * поимённо»), поэтому вход клетки есть запись без ключа, а не конверт без
 * имени. Ключ, заданный пустой строкой, — другой вход и другой сегодняшний
 * исход: он в {@link EmptyMandatoryValuesBoxTest}.
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class MissingTenantBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-2", Map.of());
    }

    @Test
    @DisplayName("B2.2 — Отсутствует тенант")
    void aRecordWithoutAKeyStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, occurredAt), null, Bodies.reference());

        assertReceptionHalted();
    }
}
