package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.10} — флаг остановки переживает откат транзакции приёма
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Предмет — ГРАНИЦА транзакций, а не сам флаг.</b> Одной транзакцией со
 * следствием ложится то, что есть следствие принятого сообщения; свидетельство
 * о ходе приёма ложится отдельной
 * (docs/rules/durable-consumer-reception.md §«Транзакционные границы — критерий
 * один и механический: одной транзакцией со следствием ложится то, что есть
 * СЛЕДСТВИЕ принятого сообщения; свидетельство о самом ходе приёма ложится
 * отдельной»). Флаг, положенный в транзакцию обработки, откатился бы вместе с
 * ней — то есть не появился бы ровно в том случае, ради которого заведён
 * ({@code StatisticsReceptionService.noteHalt}, {@code REQUIRES_NEW}).
 *
 * <p><b>Откат наблюдается ОТСУТСТВИЕМ строки факта</b>: обработка дошла до
 * вставки настолько, насколько дошла, и ничего после себя не оставила, — а флаг
 * при этом лежит.
 *
 * <p><b>Момент обновления строки состояния флагом не двигается</b>, и это
 * утверждение о ПИСАТЕЛЕ: колонку ведёт только тик
 * (docs/rules/writer-named-for-every-value.md). Обе стороны предъявлены: после
 * отказа момент тот же, после такта — другой, а флаг такт не снимает.
 */
class HaltSurvivesRollbackBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-10");
    }

    @Test
    @DisplayName("B2.10 — Флаг остановки переживает откат транзакции приёма")
    void theHaltFlagOutlivesTheRollbackOfTheReceptionTransaction() {
        givenReceptionStateRows();
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        Object updatedBeforeFailure = pair(topic()).get(UPDATED_COLUMN);

        poisonWithout(EVENT_ID);

        assertReceptionHalted();
        assertThat(pair(topic()).get(UPDATED_COLUMN))
                .as("флаг момента обновления не двигает — его пишет тик")
                .isEqualTo(updatedBeforeFailure);

        tick();

        assertThat(pair(topic()).get(UPDATED_COLUMN))
                .as("а такт тика — двигает").isNotEqualTo(updatedBeforeFailure);
        assertThat(pair(topic()).get(HALTED_COLUMN))
                .as("и флага при этом не снимает: снимает его только приём").isEqualTo(Boolean.TRUE);
    }
}
