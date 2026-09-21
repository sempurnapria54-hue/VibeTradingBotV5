package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.13} — строки пары ещё нет: свидетельство писать некуда
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Предусловие ставится НЕОТРАБОТАВШИМ тиком, а не выключателем.</b>
 * Такт тика в прогоне подаёт сам кейс ({@link StatisticsBox#tick()}), а
 * расписание заглушено выражением, до которого прогон не доживает: состояние
 * «контейнер поднят, строк пары нет» достижимо без сдвига единственной оси,
 * которая у клетки ещё понадобится — тик обязан отработать во ВТОРОЙ половине.
 *
 * <p><b>Живость приёма предъявляется годной записью ДО отравленной, и это
 * несущая часть клетки.</b> Без неё «строк нет, смещение стои́т» было бы верно
 * и у контекста, чей слушатель не получил назначения вовсе: ассерт сошёлся бы
 * по причине, которой клетка не ставила. Принятая годная запись двигает
 * смещение на единицу — и дальше оно не двигается ни разу.
 *
 * <p><b>Клетка КРАСНАЯ по построению, и красной её делает дерево кода.</b> Дом
 * объявляет окно ненаблюдаемости ОГРАНИЧЕННЫМ одним тактом тика: отравленное
 * сообщение доставляется повторно, тик тем временем работает, и следующая
 * попытка застаёт строку заведённой
 * (docs/rules/durable-consumer-reception.md §«Строки пары ещё нет — не пишется
 * ничего, и это верно у всех величин приёма»). Писатель флага при этом пишет
 * ТОЛЬКО на первой доставке ({@code ReceptionHaltMarker}), а первая пришлась
 * ровно на окно — и флаг не ложится НИКОГДА: приём стои́т навсегда, а строка
 * состояния после такта говорит «остановки не было». Ошибка в разрешающую
 * сторону у величины, чей единственный предмет — сказать, что приём встал
 * (находка {@code F-10}, .claude/work/backlog.md §«Флаг остановки приёма,
 * потерянный до первого такта тика, не ложится никогда»).
 *
 * <p><b>До такта выдача чтения даёт отсутствующую границу и «не
 * утверждаема».</b> Оба ответа об одном: подписанных пар нет, обещать нечего,
 * и свёртка обязана сказать это обеими величинами разом
 * (docs/spec/durable-reception.json, {@code pairsObserved}).
 */
@Tag("debt")
class UntickedPairsBoxTest extends PoisonedReceptionBox {

    /** Окно наблюдения: несколько пауз повтора подряд. */
    private static final Duration OBSERVATION = Duration.ofSeconds(12);

    /** Смещение, до которого доехала единственная принятая запись. */
    private static final Long CONSUMED_LIVE_RECORD = 1L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-13");
    }

    @Test
    @DisplayName("B2.13 — Строки пары ещё нет: свидетельство писать некуда")
    void withoutAPairRowThereIsNowhereToWriteTheEvidence() {
        assertThat(rows.count(RECEPTION_TABLE)).as("строк состояния нет ни одной").isZero();
        publish("E-LIVE", DEAL_CLOSED, momentsAgo(Duration.ofMinutes(5)),
                Bodies.dealClosed(ACCOUNT, "S-1"));
        awaitConsumed();
        assertThat(dealFacts()).as("приём жив").hasSize(1);
        assertThat(rows.count(RECEPTION_TABLE)).as("приём строки состояния не заводит").isZero();
        rows.clear();

        Wire.publish(topic(), TENANT, envelopeWithout(EVENT_ID), Bodies.dealClosed(ACCOUNT, "S-1"));
        publish("E-AFTER", DEAL_CLOSED, momentsAgo(Duration.ofMinutes(1)),
                Bodies.dealClosed(ACCOUNT, "S-1"));

        Awaitility.await()
                .during(OBSERVATION)
                .atMost(OBSERVATION.plus(RECEPTION_WAIT))
                .pollInterval(POLL)
                .until(this::receptionStoppedWithoutEvidence);
        assertThat(lowerBound()).as("границы нет, и это значение").isNull();
        assertThat(continuityClaimable()).as("непрерывность не утверждаема").isEqualTo(Boolean.FALSE);

        givenReceptionStateRows();

        // ДОМ ОБЕЩАЕТ ОКНО В ОДИН ТАКТ: строка заведена, повторы идут — флаг
        // обязан лечь. Сегодня он не ложится никогда (F-10), и клетка красна
        // здесь.
        awaitHalted();
        assertThat(continuityClaimable())
                .as("числа не зовутся полными, пока приём стои́т").isEqualTo(Boolean.FALSE);
    }

    /** Строк нет ни в одной таблице, смещение стои́т на принятой записи. */
    private Boolean receptionStoppedWithoutEvidence() {
        return Objects.equals(rows.count(DEAL_FACTS), 0L)
                && Objects.equals(rows.count(RECEPTION_TABLE), 0L)
                && Objects.equals(CONSUMED_LIVE_RECORD,
                        Wire.committedOffset(consumerGroup(), topic()));
    }
}
