package com.example.statistics.box;

import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.9} — пустые значения обязательных полей входа отвергаются
 * наравне с отсутствующими (.claude/tests/cases/statistics.md).
 *
 * <p><b>Ожидание взято из дома:</b> умолчание не бывает благоприятным, и
 * ошибка направляется в запретительную сторону (docs/concept.md, П1).
 * Обязательность входа мерится <b>непустым значением</b>, а не ненулевой
 * ссылкой ({@code DealFact.hasCompleteInput}): факт с пустой идентичностью
 * схлопнул бы о свою отметку обработанного всякое следующее событие того же
 * момента, строка с пустым тенантом была бы невидима любой выборке чтения, а
 * строка с пустым биржевым счётом дала бы зерно агрегата, которого не
 * прочитает никто (находка {@code F-4}, закрыта заходом 229).
 *
 * <p><b>Свой контекст у клетки — потому, что она травит приём:</b> положенная
 * в общий ящик, она заняла бы его единственный поток слушателя и уронила бы
 * соседей вместо себя.
 *
 * <p><b>Исход ждётся ДВУСТОРОННИМ условием</b> — флаг остановки либо
 * появившиеся строки, — и это не смягчение ассерта: при вернувшемся дефекте
 * ожидание одного лишь флага истекало бы по таймауту целую минуту, сообщая
 * ровно то же самое шестьюдесятью секундами позже.
 */
class EmptyMandatoryValuesBoxTest extends PoisonedReceptionBox {

    /** Сколько записей кладёт клетка: пустые идентичность, тенант и счёт. */
    private static final Long PUBLISHED = 3L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-9");
    }

    @Test
    @DisplayName("B2.9 — Пустые значения обязательных полей входа отвергаются наравне с отсутствующими")
    void emptyMandatoryValuesAreRejectedLikeAbsentOnes() {
        givenReceptionStateRows();

        Wire.publish(topic(), TENANT, envelopeWith(EVENT_ID, ""),
                Bodies.dealClosed(ACCOUNT, "S-1"));
        Wire.publish(topic(), "", envelope("E-9b", DEAL_CLOSED, occurredAt),
                Bodies.dealClosed(ACCOUNT, "S-1"));
        Wire.publish(topic(), TENANT, envelope("E-9c", DEAL_CLOSED, occurredAt),
                Bodies.dealClosed("", "S-1"));
        awaitSettled();

        assertReceptionHalted(PUBLISHED);
    }

    /**
     * Ждёт, пока все три записи получат исход: приём встал либо строки легли.
     *
     * <p>Второе условие и есть сегодняшнее поведение; на нём клетка падает,
     * предъявляя долг.
     */
    private void awaitSettled() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Boolean.TRUE, pair(topic()).get(HALTED_COLUMN))
                        || Objects.equals(rows.count(DEAL_FACTS), PUBLISHED));
    }
}
