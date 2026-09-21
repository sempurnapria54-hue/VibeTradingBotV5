package com.example.audit.box;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетки группы {@code B4}, у которых момент обнаружения разрыва —
 * ДОСТАВКА записи, а не назначение партиций (.claude/tests/cases/audit.md
 * §«B4 — Обнаружение разрыва: три исхода сравнения смещений»).
 *
 * <p><b>Все четыре берут штатное положение осей и живут одним классом:</b>
 * приём у них жив, назначение не меняется, и вход подаётся записью в тему
 * — ровно то, ради чего заведён второй момент сравнения
 * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва —
 * сравнение смещений, и моментов у него два»).
 *
 * <p><b>Смещение доставленной записи поднимается УПРАВЛЯЮЩЕЙ записью
 * транзакции</b> ({@link Wire#publishInTransaction}): она занимает своё
 * смещение и потребителю не отдаётся, поэтому следующая обычная приезжает
 * на единицу дальше ожидаемого. Замена повода объявлена у самого хода: в
 * проде повод один — удаление на ходу, — но оно гонкой с живым
 * потребителем не выражается, а наблюдаемое у клетки есть смещение
 * записи, и повод его роста клетке безразличен.
 *
 * <p><b>Повторная доставка ставится ОТКАЗОМ обработки</b>
 * ({@link Rows#withoutTable}): это единственная тропа, на которой брокер
 * отдаёт потребителю ТУ ЖЕ запись с тем же смещением. Вторая годная
 * запись была бы другим смещением, то есть другим предметом.
 */
class DeliveryGapBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходят все клетки класса. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Тема второго производителя: ею наблюдается радиус разрыва. */
    private static final String STRATEGY = AuditSubstrate.STRATEGY_TOPIC;

    /** Колонка идентичности события: ею находится строка нужной записи. */
    private static final String EVENT_ID_COLUMN = "event_id";

    /** Возраст события, которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Идентичности записей, идущих подряд за записью с пропуском. */
    private static final List<String> AFTER_GAP = List.of("E-N1", "E-N2", "E-N3");

    @Test
    @DisplayName("B4.5 — Разрыв на доставке: смещение записи больше ожидаемого")
    void aRecordDeliveredAboveTheExpectedOffsetIsAGap() {
        givenReceptionStateRows();
        givenExpectationRaised();

        publish(CORE, "E-GAP", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);

        assertThat(pair(CORE).get(GAP_COLUMN)).as("момент разрыва поставлен").isNotNull();
        assertThat(instant(pair(CORE), GAP_COLUMN))
                .as("поставлен он ДО приёма: разрыв есть свидетельство о ходе приёма, а не следствие записи")
                .isBefore(instant(recordOf("E-GAP"), RECORDED_COLUMN));
        assertThat(records()).as("строка самой записи записана: разрыв приёму не мешает").hasSize(2);
        assertThat(pair(CORE).get(HALTED_COLUMN))
                .as("приём при этом не останавливался").isEqualTo(Boolean.FALSE);
        assertThat(continuityClaimable()).as("предикат непрерывности ложен").isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B4.7 — Повторно доставленная запись разрывом не считается")
    void aRedeliveredRecordIsNotCountedAsAGap() {
        givenReceptionStateRows();

        rows.withoutTable(JOURNAL_TABLE, () -> {
            publish(CORE, "E-AGAIN", momentsAgo(EVENT_AGE), Bodies.reference());
            awaitHalted(CORE);
        });
        awaitConsumed(CORE);

        assertThat(pair(CORE).get(GAP_COLUMN))
                .as("смещение повтора не больше ожидаемого, и разрывом он не объявляется").isNull();
        assertThat(records()).as("второй строки журнала нет: конфликт по ключу поглощён").hasSize(1);
        assertThat(pair(CORE).get(HALTED_COLUMN))
                .as("приём возобновился, и флаг снят принятой записью").isEqualTo(Boolean.FALSE);
        assertThat(continuityClaimable()).as("предикат непрерывности истинен").isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B4.8 — Одна дыра объявляется один раз")
    void aSingleHoleIsDeclaredExactlyOnce() {
        givenReceptionStateRows();
        givenExpectationRaised();
        publish(CORE, "E-GAP", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);
        Object gapMoment = pair(CORE).get(GAP_COLUMN);
        assertThat(gapMoment).as("момент разрыва поставлен первой записью").isNotNull();

        for (String eventId : AFTER_GAP) {
            publish(CORE, eventId, momentsAgo(Duration.ofMinutes(1)), Bodies.reference());
        }
        awaitConsumed(CORE);

        assertThat(pair(CORE).get(GAP_COLUMN))
                .as("идущие следом записи момента не переписывают: ожидание сдвигается и при разрыве")
                .isEqualTo(gapMoment);
        assertThat(records())
                .as("записаны все: запись-затравка, запись с пропуском и три следом").hasSize(5);
    }

    @Test
    @DisplayName("B4.10 — Разрыв на одной теме предикат соседней не роняет")
    void aGapOnOneTopicDoesNotBreakTheNeighbouringPair() {
        givenReceptionStateRows();
        givenExpectationRaised();
        publish(STRATEGY, "E-NEIGHBOUR", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(STRATEGY);

        publish(CORE, "E-GAP", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);

        assertThat(pair(CORE).get(GAP_COLUMN)).as("момент разрыва поставлен у своей пары").isNotNull();
        assertThat(pair(STRATEGY).get(GAP_COLUMN)).as("у второй он пуст").isNull();
        assertThat(pair(STRATEGY).get(HALTED_COLUMN))
                .as("и приём по ней не остановлен").isEqualTo(Boolean.FALSE);
        assertThat(continuityClaimable())
                .as("групповой предикат ложен: дыра хоть на одной теме есть дыра в принятом")
                .isEqualTo(Boolean.FALSE);
    }

    /**
     * Поднимает КОНЕЦ темы над ожидаемым смещением, не двигая самого
     * ожидания: запись едет транзакцией, и её коммит кладёт управляющую
     * запись, которой потребитель не видит.
     */
    private void givenExpectationRaised() {
        Wire.publishInTransaction(CORE, TENANT, envelope("E-SEED", momentsAgo(EVENT_AGE)),
                Bodies.reference());
        awaitRecordCount(1L);
    }

    /** Строка журнала названного события; иное число — падение. */
    private Map<String, Object> recordOf(String eventId) {
        return records().stream()
                .filter(row -> eventId.equals(row.get(EVENT_ID_COLUMN)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Строки события в журнале нет: " + eventId));
    }
}
