package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.14} — восстановление после починки производителя
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Отравленная запись уходит ИСТЕЧЕНИЕМ СРОКА ХРАНЕНИЯ, и ставится это
 * удалением записей ниже конца темы</b> ({@link Wire#deleteRecordsBefore}):
 * срок — величина окружения, отмеряемая часами брокера, ждать которой прогон
 * не может, а наблюдаемое у обоих поводов одно — наименьшее доступное смещение
 * поднялось над непрочитанной записью. Заменить её годной «тем же смещением»
 * нельзя: тема неизменяема.
 *
 * <p><b>Момент разрыва — durable-ВХОД клетки, а не её выход.</b> Кейс
 * утверждает, что разрыв, если он был записан, <b>не гаснет</b>: гасящего
 * писателя у него нет вовсе — гасит разрыв чистка следствий, а чистки у фактов
 * не существует (docs/models/domain/other/StatisticsFact.md §«Состояние приёма
 * и полнота чисел статистики»). Поставить его тропой ящика значило бы заодно
 * двинуть смещения, о неподвижности которых клетка утверждает.
 *
 * <p><b>Предикат непрерывности остаётся ложным НАВСЕГДА</b>, и это верно по
 * существу: дыра в принятом безвозвратна, а снятый флаг остановки говорит лишь
 * о том, что приём пошёл дальше.
 */
class ProducerRecoveryBoxTest extends PoisonedReceptionBox {

    /** Смещение, ниже которого записей не остаётся: отравленная — первая. */
    private static final Long AFTER_POISON = 1L;

    /** Смещение, зафиксированное группой после принятой годной записи. */
    private static final Long CONSUMED_BOTH = 2L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-14");
    }

    @Test
    @DisplayName("B2.14 — Восстановление после починки производителя")
    void receptionResumesOnceTheProducerIsFixed() {
        givenReceptionStateRows();
        OffsetDateTime gapAt = momentsAgo(Duration.ofHours(1));
        givenGapOf(topic(), gapAt);
        poisonWithout(EVENT_ID);
        OffsetDateTime healthy = momentsAgo(Duration.ofMinutes(1));
        publish("E-OK", DEAL_CLOSED, healthy, Bodies.dealClosed(ACCOUNT, "S-1"));

        Wire.deleteRecordsBefore(topic(), AFTER_POISON);
        assertThat(Wire.earliestOffset(topic()))
                .as("вход поставлен: отравленной записи в теме больше нет").isEqualTo(AFTER_POISON);

        awaitRecovered();

        assertThat(dealFact().get("event_id")).as("годная запись принята").isEqualTo("E-OK");
        assertThat(instant(pair(topic()), LAST_ACCEPTED_COLUMN))
                .as("момент последнего принятого двинулся").isEqualTo(healthy.toInstant());
        assertThat(pair(topic()).get(HALTED_COLUMN))
                .as("флаг остановки снят приёмом").isEqualTo(Boolean.FALSE);
        assertThat(Wire.endOffset(topic()))
                .as("лаг по паре убыл до нуля").isEqualTo(CONSUMED_BOTH);
        assertThat(instant(pair(topic()), GAP_COLUMN))
                .as("момент разрыва не гаснет: гасящего писателя у него нет")
                .isEqualTo(gapAt.toInstant());
        assertThat(continuityClaimable())
                .as("предикат непрерывности ложен навсегда: дыра безвозвратна")
                .isEqualTo(Boolean.FALSE);
    }

    /** Ждёт, пока группа зачтёт обе записи темы: отравленной больше нет. */
    private void awaitRecovered() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(CONSUMED_BOTH,
                        Wire.committedOffset(consumerGroup(), topic())));
    }
}
