package com.example.audit.box;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.6} — тело, заданное пустой строкой
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Звено то же, что у записи без тела, — предикат полноты.</b> После
 * закрытия находки {@code F-1} его конъюнкт {@code isNotBlank(content)}
 * ({@code AuditRecord#hasCompleteInput}) мерит непустое значение, а не
 * ненулевую ссылку, и до приведения к документу на стороне базы такая
 * запись не доходит.
 *
 * <p><b>Свой класс у входа потому, что он — второе отравленное
 * сообщение</b>, а два таких в одном контексте не уживаются
 * ({@link PoisonedReceptionBox}); запись без тела —
 * {@link MissingContentBoxTest}.
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}): строки журнала
 * нет, смещение группы не продвинулось, флаг остановки лежит у своей пары,
 * непрерывность не утверждаема, наружу не ушло ничего.
 */
class EmptyContentBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-6b", Map.of());
    }

    @Test
    @DisplayName("B2.6 — Содержимое пустой строкой")
    void anEmptyStringBodyStopsReceptionAtTheCompletenessPredicate() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, occurredAt), TENANT, "");

        assertReceptionHalted();
    }
}
