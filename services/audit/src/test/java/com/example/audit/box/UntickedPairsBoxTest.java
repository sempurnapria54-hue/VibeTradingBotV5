package com.example.audit.box;

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
 * Клетка {@code B2.14} — строки пары ещё нет: свидетельство писать некуда
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Выключатель тика — ВХОД клетки</b>, поэтому контекст у неё свой:
 * состав пар ведёт только тик, и при снятом выключателе строк состояния не
 * заводит никто (docs/rules/durable-consumer-reception.md §«Строки пары ещё
 * нет — не пишется ничего, и это верно у всех величин приёма»). Величины
 * приёма пишутся ОБНОВЛЕНИЕМ существующей строки, и обновление не находит
 * цели — ни флаг остановки, ни момент разрыва вставки не делают.
 *
 * <p><b>Живость приёма предъявляется годной записью ДО отравленной, и это
 * несущая часть клетки.</b> Без неё «строк нет, смещение стои́т» было бы
 * верно и у контекста, чей слушатель не получил назначения вовсе: ассерт
 * сошёлся бы по причине, которой клетка не ставила. Принятая годная запись
 * двигает смещение на единицу — и дальше оно не двигается ни разу.
 *
 * <p><b>Остановку предъявляет СЛЕДУЮЩАЯ за отравленной годная запись.</b>
 * Поток у слушателя один, и её непоявление в журнале есть остановка всего
 * приёма, а не пропуск одной записи.
 *
 * <p><b>Выдача чтения на этом состоянии даёт отсутствующую границу и «не
 * утверждаема».</b> Оба ответа об одном: подписанных пар нет, обещать
 * нечего, и свёртка обязана сказать это обеими величинами разом
 * (docs/spec/durable-reception.json, {@code pairsObserved}).
 */
class UntickedPairsBoxTest extends PoisonedReceptionBox {

    /** Окно наблюдения: несколько пауз повтора подряд. */
    private static final Duration OBSERVATION = Duration.ofSeconds(12);

    /** Смещение, до которого доехала единственная принятая запись. */
    private static final Long CONSUMED_LIVE_RECORD = 1L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-14",
                Map.of(AuditSubstrate.STATE_TICK_ENABLED_KEY, "false"));
    }

    @Test
    @DisplayName("B2.14 — Строки пары ещё нет: свидетельство писать некуда")
    void withoutAPairRowThereIsNowhereToWriteTheEvidence() {
        assertThat(rows.count(RECEPTION_TABLE)).as("строк состояния нет ни одной").isZero();
        publish(poisonedTopic(), "E-LIVE", momentsAgo(Duration.ofMinutes(5)), Bodies.reference());
        awaitConsumed(poisonedTopic());
        assertThat(records()).as("приём жив").hasSize(1);
        assertThat(rows.count(RECEPTION_TABLE)).as("приём строки состояния не заводит").isZero();
        rows.clear();

        Wire.publish(poisonedTopic(), TENANT, envelopeWithout(EVENT_ID), Bodies.reference());
        publish(poisonedTopic(), "E-AFTER", momentsAgo(Duration.ofMinutes(1)), Bodies.reference());

        Awaitility.await()
                .during(OBSERVATION)
                .atMost(OBSERVATION.plus(RECEPTION_WAIT))
                .pollInterval(POLL)
                .until(this::receptionStoppedWithoutEvidence);
        assertThat(lowerBound()).as("границы нет, и это значение").isNull();
        assertThat(continuityClaimable()).as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);
    }

    /** Строк нет ни в одной таблице, смещение стои́т на принятой записи. */
    private Boolean receptionStoppedWithoutEvidence() {
        return Objects.equals(rows.count(JOURNAL_TABLE), 0L)
                && Objects.equals(rows.count(RECEPTION_TABLE), 0L)
                && Objects.equals(CONSUMED_LIVE_RECORD,
                        Wire.committedOffset(consumerGroup(), poisonedTopic()));
    }
}
