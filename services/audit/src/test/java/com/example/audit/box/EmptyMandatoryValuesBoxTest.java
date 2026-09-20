package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.10} — пустые значения обязательных полей входа
 * отвергаются наравне с отсутствующими (.claude/tests/cases/audit.md).
 *
 * <p><b>Клетка КРАСНАЯ по построению, и красной её делает дерево кода, а
 * не ожидание.</b> Ожидание взято из дома: умолчание не бывает
 * благоприятным, и ошибка направляется в запретительную сторону
 * (docs/concept.md, П1). Исполнитель мерит обязательность входа
 * <b>ненулевой ссылкой</b>, а не непустым значением, и обе записи сегодня
 * принимаются: первая даёт строку с пустой идентичностью, о которую дедуп
 * схлопнет всякое следующее событие с тем же пустым значением; вторая —
 * строку, невидимую любому читателю, потому что тенант обязателен и непуст
 * на каждой тропе чтения. Оба исхода суть молчаливая потеря факта ровно
 * там, где конструкция обещает громкую остановку (находка {@code F-1},
 * .claude/work/backlog.md §«Обязательность входа журнала мерится ссылкой,
 * а не значением»).
 *
 * <p><b>Свой контекст у клетки НЕ потому, что она травит приём сегодня</b>
 * — сегодня обе записи проходят, — <b>а потому, что она обязана травить
 * его завтра.</b> Положенная в общий ящик, она заняла бы его единственный
 * поток слушателя в тот же ход, которым долг будет закрыт, и уронила бы
 * соседей вместо себя.
 *
 * <p><b>Исход ждётся ДВУСТОРОННИМ условием</b> — флаг остановки либо
 * появившиеся строки, — и это не смягчение ассерта: ожидание одного лишь
 * флага истекало бы по таймауту целую минуту, сообщая ровно то же самое
 * шестьюдесятью секундами позже.
 */
@Tag("debt")
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
     * <p>Второе условие и есть сегодняшнее поведение; на нём клетка
     * падает, предъявляя долг.
     */
    private void awaitSettled() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Boolean.TRUE, pair(poisonedTopic()).get(HALTED_COLUMN))
                        || Objects.equals(rows.count(JOURNAL_TABLE), PUBLISHED));
    }
}
