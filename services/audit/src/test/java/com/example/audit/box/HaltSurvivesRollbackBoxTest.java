package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.11} — флаг остановки переживает откат транзакции приёма
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Предмет — ГРАНИЦА транзакций, а не сам флаг.</b> Одной транзакцией
 * со следствием ложится то, что есть следствие принятого сообщения;
 * свидетельство о ходе приёма ложится отдельной
 * (docs/rules/durable-consumer-reception.md §«Транзакционные границы»).
 * Флаг, положенный в транзакцию обработки, откатился бы вместе с ней — то
 * есть не появился бы ровно в том случае, ради которого заведён.
 *
 * <p><b>Откат наблюдается ОТСУТСТВИЕМ строки журнала</b>: обработка дошла
 * до вставки настолько, насколько дошла, и ничего после себя не оставила,
 * — а флаг при этом лежит.
 *
 * <p><b>Момент обновления строки состояния флагом не двигается</b>, и это
 * утверждение о ПИСАТЕЛЕ: колонку ведёт только тик
 * (docs/rules/writer-named-for-every-value.md). Обе стороны предъявлены:
 * после отказа момент тот же, после такта — другой, а флаг такт не
 * снимает.
 */
class HaltSurvivesRollbackBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b2-11", Map.of());
    }

    @Test
    @DisplayName("B2.11 — Флаг остановки переживает откат транзакции приёма")
    void theHaltFlagOutlivesTheRollbackOfTheReceptionTransaction() {
        givenReceptionStateRows();
        assertThat(pair(poisonedTopic()).get(HALTED_COLUMN)).isEqualTo(Boolean.FALSE);
        Object updatedBeforeFailure = pair(poisonedTopic()).get(UPDATED_COLUMN);

        poisonWithout(EVENT_ID);

        assertReceptionHalted();
        assertThat(pair(poisonedTopic()).get(UPDATED_COLUMN))
                .as("флаг момента обновления не двигает — его пишет тик")
                .isEqualTo(updatedBeforeFailure);

        tick();

        assertThat(pair(poisonedTopic()).get(UPDATED_COLUMN))
                .as("а такт тика — двигает").isNotEqualTo(updatedBeforeFailure);
        assertThat(pair(poisonedTopic()).get(HALTED_COLUMN))
                .as("и флага при этом не снимает: снимает его только приём").isEqualTo(Boolean.TRUE);
    }
}
