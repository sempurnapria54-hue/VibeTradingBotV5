package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B4.9} — момент разрыва переживает откат транзакции приёма
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Предмет — ГРАНИЦА транзакций, а не сам момент.</b> Одной
 * транзакцией со следствием ложится то, что есть следствие принятого
 * сообщения; свидетельство о самом ходе приёма ложится отдельной
 * (docs/rules/durable-consumer-reception.md §«Транзакционные границы»).
 * Момент разрыва, положенный в транзакцию обработки, откатился бы вместе
 * с ней — то есть не появился бы ровно в том случае, когда о ходе приёма
 * известно меньше всего.
 *
 * <p><b>Вход у клетки ДВОЙНОЙ: запись приходит и с пропуском, и с неполным
 * конвертом.</b> Пропуск ставится управляющей записью транзакции, поднявшей
 * конец темы над ожидаемым смещением ({@link Wire#publishInTransaction});
 * неполнота — отсутствием заголовка идентичности, как у всей группы
 * {@code B2}.
 *
 * <p><b>Контекст у класса свой и закрывается вместе с ним</b>
 * ({@link PoisonedReceptionBox}): отравленное сообщение повторяется без
 * ограничения числа попыток и занимает единственный поток слушателя.
 */
class GapSurvivesRollbackBoxTest extends PoisonedReceptionBox {

    /** Сколько строк журнала лежит к моменту отравления: одна затравка. */
    private static final Long SEEDED = 1L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, "b4-9", Map.of());
    }

    @Test
    @DisplayName("B4.9 — Момент разрыва переживает откат транзакции приёма")
    void theGapMomentOutlivesTheRollbackOfTheReceptionTransaction() {
        givenReceptionStateRows();
        Wire.publishInTransaction(poisonedTopic(), TENANT, envelope("E-SEED", occurredAt),
                Bodies.reference());
        awaitRecordCount(SEEDED);
        assertThat(pair(poisonedTopic()).get(GAP_COLUMN)).as("разрыва до входа нет").isNull();

        poisonWithout(EVENT_ID);

        assertThat(pair(poisonedTopic()).get(GAP_COLUMN))
                .as("момент разрыва поставлен, хотя обработка сообщения откатилась").isNotNull();
        assertThat(records()).as("строки журнала отравленной записи нет").hasSize(1);
        assertThat(pair(poisonedTopic()).get(HALTED_COLUMN))
                .as("флаг остановки лежит").isEqualTo(Boolean.TRUE);
        assertThat(pair(neighbourTopic()).get(GAP_COLUMN))
                .as("разрыв лёг у своей пары, а не у группы").isNull();
        assertThat(continuityClaimable())
                .as("предикат ложен по обеим ветвям сразу").isEqualTo(Boolean.FALSE);
    }
}
