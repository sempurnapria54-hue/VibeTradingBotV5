package com.example.audit.box;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B10.5} — автоматической фиксации смещения нет: порядок
 * «следствие, затем смещение» (.claude/tests/cases/audit.md §«B10 —
 * Конфигурация и схема как вход»).
 *
 * <p><b>Отказ базы ПОСРЕДИ приёма выражен отсутствием таблицы</b>
 * ({@link Rows#withoutTable}) — той же формой, которой группы {@code B3} и
 * {@code B9} выражают отказ базы посреди своего хода. Остановка контейнера
 * базы дала бы ту же тропу ценой ожидания пула соединений и не изменила бы
 * ни одного наблюдаемого.
 *
 * <p><b>Отказ здесь ВРЕМЕННЫЙ, и в этом предмет клетки.</b> Соседние
 * клетки группы {@code B2} травят приём НЕПОПРАВИМЫМ входом и смотрят, что
 * смещение стои́т; здесь причина отказа снимается — и наблюдаемым
 * становится то, что группа вернулась к ТОЙ ЖЕ записи, а не перешагнула
 * её. Ровно это отличает выключенную автоматическую фиксацию от
 * включённой: при включённой таймер клиента продвинул бы смещение
 * независимо от того, легла ли строка, и повторной доставки не случилось
 * бы никогда.
 *
 * <p><b>Своя группа и свои темы обязательны:</b> смещение есть состояние
 * на брокере, общее всем контекстам одного имени группы.
 *
 * <p><b>Пауза повтора штатная</b> — величина клетке безразлична, её предмет
 * несёт соседняя ({@code B10.6}).
 */
class OffsetAfterConsequenceBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b10-5";

    /** Идентичность события, чья обработка отказывает на записи в базу. */
    private static final String EVENT = "E-B10-5";

    /** Возраст события, с которым клетка ходит в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /**
     * Срок, в течение которого смещение не продвигается.
     *
     * <p><b>Отрицание ДЕРЖИТСЯ окном, а не снимается мгновением, и это не
     * перестраховка.</b> Таймер автоматической фиксации бьёт раз в пять
     * секунд (умолчание клиента), поэтому снимок, взятый сразу после
     * первой неудачной доставки, у включённой фиксации и у выключенной
     * одинаков — клетка мерила бы скорость своей расстановки. Окно шире
     * такта таймера: при включённой фиксации внутри него смещение
     * продвинется.
     */
    private static final Duration WITHOUT_COMMIT = Duration.ofSeconds(8);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG, Map.of());
    }

    @Test
    @DisplayName("B10.5 — Автоматической фиксации смещения нет: порядок «следствие, затем смещение»")
    void noOffsetIsCommittedUntilTheConsequenceIsWritten() {
        givenReceptionStateRows();

        rows.withoutTable(JOURNAL_TABLE, () -> {
            publish(subject(), EVENT, momentsAgo(EVENT_AGE), Bodies.reference());
            awaitHalted(subject());
            Awaitility.await()
                    .during(WITHOUT_COMMIT)
                    .atMost(WITHOUT_COMMIT.plusSeconds(2))
                    .pollInterval(POLL)
                    .until(() -> isNull(Wire.committedOffset(consumerGroup(), subject())));
            assertThat(Wire.endOffset(subject()))
                    .as("запись лежит непринятой: лаг по паре равен ей одной")
                    .isEqualTo(1L);
        });

        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(JOURNAL_TABLE), 1L));
        awaitConsumed(subject());

        assertThat(record().get("event_id"))
                .as("после восстановления базы принято ТО ЖЕ сообщение: группа его не перешагнула")
                .isEqualTo(EVENT);
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("лаг вернулся к нулю только ПОСЛЕ записи следствия")
                .isEqualTo(Wire.endOffset(subject()));
    }

    /** Тема, в которую клетка кладёт запись. */
    private String subject() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }
}
