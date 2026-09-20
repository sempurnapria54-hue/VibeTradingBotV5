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
 * Клетка {@code B2.12} — повторы идут без ограничения числа попыток
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Предмет — ОТСУТСТВИЕ предела, и мерится оно предъявлением
 * числа, за которым умолчание уже сдалось бы.</b> Умолчание каркаса —
 * конечное число попыток, затем восстановление логированием и
 * <b>продвижение смещения</b>; при нём «смещение продвинулось, строки нет»
 * становится штатным исходом, а у журнала пропущенное не восстанавливается
 * ничем (docs/rules/durable-consumer-reception.md §«Обработчик отказа —
 * часть конструкции, а не настройка, и признак у него механический:
 * восстановимо ли пропущенное»). Клетка наблюдает сотню пауз повтора
 * подряд — на порядок больше любого умолчания, — и всё это время смещение
 * стои́т.
 *
 * <p><b>Пауза повтора у контекста своя, и это не подкрутка предмета.</b>
 * Входом клетки является число попыток, а не их частота: пауза управляет
 * только тем, как часто повтор бьётся в брокер и базу
 * ({@code ReceptionProperties#retryInterval}). Сжатая, она даёт ту же сотню
 * попыток за время, которое прогон может себе позволить.
 *
 * <p><b>Следующая за отравленной запись — годная, и она тоже не
 * обрабатывается.</b> Это вторая половина предмета: поток у слушателя один,
 * и остановка есть остановка ВСЕГО приёма, а не пропуск одной записи.
 *
 * <p><b>Возраст последнего принятого события растёт монотонно</b> — его
 * ряд снимается экспозицией до наблюдения и после. Ряд едет МОМЕНТОМ, а не
 * готовым числом, поэтому он растёт сам по себе, пока тик жив.
 */
class UnlimitedRetryBoxTest extends PoisonedReceptionBox {

    /** Пауза повтора этого контекста. */
    private static final String RETRY_INTERVAL = "200ms";

    /** Окно наблюдения: сотня пауз повтора подряд. */
    private static final Duration OBSERVATION = Duration.ofSeconds(20);

    /** Сколько записей лежит в теме: отравленная и годная следом. */
    private static final Long PUBLISHED = 2L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-12",
                Map.of(AuditSubstrate.RETRY_INTERVAL_KEY, RETRY_INTERVAL));
    }

    @Test
    @DisplayName("B2.12 — Повторы идут без ограничения числа попыток")
    void retriesRunWithoutAnyLimitOnTheirNumber() {
        givenReceptionStateRows();
        poisonWithout(EVENT_ID);
        publish(poisonedTopic(), "E-NEXT", momentsAgo(Duration.ofMinutes(1)), Bodies.reference());
        tick();
        Long ageBeforeObservation = ageRowOf(poisonedTopic());

        Awaitility.await()
                .during(OBSERVATION)
                .atMost(OBSERVATION.plus(RECEPTION_WAIT))
                .pollInterval(POLL)
                .until(this::receptionStandsStill);

        tick();
        assertThat(Wire.endOffset(poisonedTopic()))
                .as("лаг по паре не убывает: обе записи лежат непринятыми").isEqualTo(PUBLISHED);
        assertThat(pair(poisonedTopic()).get(HALTED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(ageRowOf(poisonedTopic()))
                .as("возраст последнего принятого растёт монотонно")
                .isGreaterThan(ageBeforeObservation);
    }

    /** Смещение стои́т, строк журнала нет — и так весь срок наблюдения. */
    private Boolean receptionStandsStill() {
        return isNull(Wire.committedOffset(consumerGroup(), poisonedTopic()))
                && Objects.equals(rows.count(JOURNAL_TABLE), 0L);
    }
}
