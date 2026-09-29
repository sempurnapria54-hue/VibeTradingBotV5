package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.10} — пустые значения обязательных полей входа
 * отвергаются наравне с отсутствующими (.claude/tests/cases/audit.md).
 *
 * <p><b>Ожидание взято из дома:</b> умолчание не бывает благоприятным, и
 * ошибка направляется в запретительную сторону (docs/concept.md, П1).
 * Обязательность входа мерится <b>непустым значением</b>, а не ненулевой
 * ссылкой ({@code AuditRecord.hasCompleteInput}): строка с пустой
 * идентичностью дала бы ключ, о который дедуп схлопнет всякое следующее
 * событие с тем же пустым значением, строка с пустым тенантом — строку,
 * невидимую любому читателю (находка {@code F-1}, закрыта заходом 229).
 *
 * <p><b>Свой контекст у клетки — потому, что она травит приём:</b>
 * положенная в общий ящик, она заняла бы его единственный поток слушателя и
 * уронила бы соседей вместо себя.
 *
 * <p><b>Исход ждётся ДВУСТОРОННИМ условием</b> — флаг остановки либо
 * появившиеся строки, — и это не смягчение ассерта: при вернувшемся дефекте
 * ожидание одного лишь флага истекало бы по таймауту целую минуту, сообщая
 * ровно то же самое шестьюдесятью секундами позже.
 */
class EmptyMandatoryValuesBoxTest extends PoisonedReceptionBox {

    /** Сколько записей кладёт клетка: пустая идентичность и пустой ключ. */
    private static final Long PUBLISHED = 2L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-10", Map.of());
    }

    @Test
    @DisplayName("B2.10 — Пустые значения обязательных полей входа отвергаются наравне с отсутствующими")
    void emptyMandatoryValuesAreRejectedLikeAbsentOnes() {
        givenReceptionStateRows();

        Wire.publish(poisonedTopic(), TENANT, envelopeWith(EVENT_ID, ""), Bodies.reference());
        Wire.publish(poisonedTopic(), "", envelope(POISON_EVENT, occurredAt), Bodies.reference());
        awaitSettled();

        assertThat(records()).as("строки журнала нет").isEmpty();
        assertThat(pair(poisonedTopic()).get(HALTED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(Wire.committedOffset(consumerGroup(), poisonedTopic()))
                .as("смещение группы не продвинулось").isNull();
        assertThat(continuityClaimable()).as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
    }

    /**
     * Ждёт, пока обе записи получат исход: приём встал либо строки легли.
     *
     * <p>Второе условие — исход вернувшегося дефекта; на нём клетка
     * падает, не дожидаясь таймаута.
     */
    private void awaitSettled() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Boolean.TRUE, pair(poisonedTopic()).get(HALTED_COLUMN))
                        || Objects.equals(rows.count(JOURNAL_TABLE), PUBLISHED));
    }
}
