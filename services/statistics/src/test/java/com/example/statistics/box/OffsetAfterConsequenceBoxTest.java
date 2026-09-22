package com.example.statistics.box;

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
 * Клетка {@code B12.6} — автоматической фиксации смещения нет: порядок
 * «следствие, затем смещение» (.claude/tests/cases/statistics.md §«B12 —
 * Конфигурация и схема как вход»).
 *
 * <p><b>Отказ базы ПОСРЕДИ приёма выражен отсутствием таблицы</b>
 * ({@link Rows#withoutTable}) — той же формой, которой группы {@code B3},
 * {@code B4} и {@code B7} выражают отказ базы посреди своего хода.
 * Остановка контейнера базы дала бы ту же тропу ценой ожидания пула
 * соединений и не изменила бы ни одного наблюдаемого.
 *
 * <p><b>Отказ здесь ВРЕМЕННЫЙ, и в этом предмет клетки.</b> Клетки группы
 * {@code B2} травят приём НЕПОПРАВИМЫМ входом и смотрят, что смещение стои́т;
 * здесь причина отказа снимается — и наблюдаемым становится то, что группа
 * вернулась к ТОЙ ЖЕ записи, а не перешагнула её. Ровно это отличает
 * выключенную автоматическую фиксацию от включённой: при включённой таймер
 * клиента продвинул бы смещение независимо от того, легла ли строка, и
 * повторной доставки не случилось бы никогда.
 *
 * <p><b>Строки состояния заводятся ДО порчи базы, и это не гигиена.</b> Флаг
 * остановки пишется обновлением существующей строки, и в окне до первого
 * такта тика ему некуда лечь ({@code B2.13}); без предусловия ожидание
 * остановки истекало бы по таймауту при исправном поведении.
 *
 * <p><b>Своя группа и своя тема обязательны:</b> смещение есть состояние на
 * брокере, общее всем контекстам одного имени группы.
 *
 * <p><b>Пауза повтора штатная</b> — величина клетке безразлична, её предмет
 * несёт соседняя ({@code B12.7}).
 */
class OffsetAfterConsequenceBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её тема. */
    private static final String SLUG = "b12-6";

    /** Идентичность события, чья обработка отказывает на записи в базу. */
    private static final String EVENT = "E-B12-6";

    /** Возраст события, с которым клетка ходит в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /**
     * Срок, в течение которого смещение не продвигается.
     *
     * <p><b>Отрицание ДЕРЖИТСЯ окном, а не снимается мгновением, и это не
     * перестраховка.</b> Таймер автоматической фиксации бьёт раз в пять
     * секунд (умолчание клиента), поэтому снимок, взятый сразу после первой
     * неудачной доставки, у включённой фиксации и у выключенной одинаков —
     * клетка мерила бы скорость своей расстановки. Окно шире такта таймера:
     * при включённой фиксации внутри него смещение продвинется.
     */
    private static final Duration WITHOUT_COMMIT = Duration.ofSeconds(8);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B12.6 — Автоматической фиксации смещения нет: порядок «следствие, затем смещение»")
    void noOffsetIsCommittedUntilTheConsequenceIsWritten() {
        givenReceptionStateRows();

        rows.withoutTable(DEAL_FACTS, () -> {
            publish(subject(), EVENT, DEAL_CLOSED, momentsAgo(EVENT_AGE),
                    Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
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
                .until(() -> Objects.equals(rows.count(DEAL_FACTS), 1L));
        awaitConsumed(subject());

        Map<String, Object> fact = dealFact();
        assertThat(fact.get("event_id"))
                .as("после восстановления базы принято ТО ЖЕ сообщение: группа его не "
                        + "перешагнула, и ветви «смещение продвинулось, строки нет» не было")
                .isEqualTo(EVENT);
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("смещение продвинулось только ПОСЛЕ записи следствия")
                .isEqualTo(Wire.endOffset(subject()));
    }

    /** Тема, в которую клетка кладёт запись. */
    private String subject() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }
}
