package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.6} — отсутствует содержимое
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Вход — запись БЕЗ ТЕЛА</b> при полном наборе заголовков:
 * содержимое есть то, ради чего строка заведена, и строки с пустым навесом
 * в журнале не возникает.
 *
 * <p><b>Вторая половина клетки живёт своим классом</b>
 * ({@link EmptyContentBoxTest}), и довод тот же, что у всей группы: тело,
 * заданное пустой строкой, — ВТОРОЕ отравленное сообщение, а два таких
 * сообщения в одном контексте не уживаются. Исход у них совпадает, а звено
 * разное, и подменять одно другим клетка не вправе (находка {@code F-1}).
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class MissingContentBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-6", Map.of());
    }

    @Test
    @DisplayName("B2.6 — Отсутствует содержимое")
    void aRecordWithoutABodyStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, occurredAt), TENANT, null);

        assertReceptionHalted();
    }
}
