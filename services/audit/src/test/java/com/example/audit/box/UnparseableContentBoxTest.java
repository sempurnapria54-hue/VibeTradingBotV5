package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.9} — содержимое не разбирается как документ
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Наблюдаемости сверх остановки у этого повода нет</b> — ни отчёта
 * аномалии, ни своего ряда, ни темы мёртвых писем, — и это состояние
 * объявлено долгом, а не свойством
 * (.claude/work/backlog.md §«Потерянное событие при неразбираемом
 * содержимом — наблюдаемости нет»).
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class UnparseableContentBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-9", Map.of());
    }

    @Test
    @DisplayName("B2.9 — Содержимое не разбирается как документ")
    void anUnparseableContentStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, occurredAt), TENANT, Bodies.notADocument());

        assertReceptionHalted();
    }
}
