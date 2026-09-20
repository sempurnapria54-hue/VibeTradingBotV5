package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.5} — отсутствует версия формы
 * (.claude/tests/cases/audit.md).
 *
 * <p>Версия хранится РЯДОМ с содержимым, а не выводится из него
 * (docs/models/domain/other/AuditRecord.md §Структура): без неё читатель
 * содержимого не знает, по какой форме его толковать.
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class MissingVersionBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-5", Map.of());
    }

    @Test
    @DisplayName("B2.5 — Отсутствует версия формы")
    void aMessageWithoutTheFormVersionStopsReception() {
        givenReceptionStateRows();

        poisonWithout(VERSION);

        assertReceptionHalted();
    }
}
