package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B4.8} — момент разрыва переживает откат транзакции приёма
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Предмет — ГРАНИЦА транзакций, а не сам момент.</b> Одной транзакцией со
 * следствием ложится то, что есть следствие принятого сообщения; свидетельство
 * о самом ходе приёма ложится отдельной
 * (docs/rules/durable-consumer-reception.md §«Транзакционные границы»). Момент
 * разрыва, положенный в транзакцию обработки, откатился бы вместе с ней — то
 * есть не появился бы ровно в том случае, когда о ходе приёма известно меньше
 * всего.
 *
 * <p><b>Вход у клетки ДВОЙНОЙ: запись приходит и с пропуском, и с неполным
 * конвертом.</b> Пропуск ставится управляющей записью транзакции, поднявшей
 * конец темы над ожидаемым смещением ({@link Wire#publishInTransaction});
 * неполнота — отсутствием заголовка идентичности, как у всей группы
 * {@code B2}.
 *
 * <p><b>Клетка предъявляет и ВТОРУЮ половину границы:</b> величины приёма лежат
 * ДВУМЯ транзакциями, а не одной — момент разрыва своей, флаг остановки своей,
 * и обе переживают откат обработки.
 *
 * <p><b>Контекст у класса свой и закрывается вместе с ним</b>
 * ({@link PoisonedReceptionBox}): отравленное сообщение повторяется без
 * ограничения числа попыток и занимает единственный поток слушателя.
 */
class GapSurvivesRollbackBoxTest extends PoisonedReceptionBox {

    /** Краткое имя клетки: из него строятся её группа и её тема. */
    private static final String SLUG = "b4-8";

    /** Сколько фактов лежит к моменту отравления: одна затравка. */
    private static final Long SEEDED = 1L;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG, Map.of());
    }

    @Test
    @DisplayName("B4.8 — Момент разрыва переживает откат транзакции приёма")
    void theGapMomentOutlivesTheRollbackOfTheReceptionTransaction() {
        givenReceptionStateRows();
        Wire.publishInTransaction(topic(), TENANT,
                envelope("E-SEED", DEAL_CLOSED, occurredAt),
                Bodies.dealClosed(ACCOUNT, "S-1"));
        awaitDealFactCount(SEEDED);
        assertThat(pair(topic()).get(GAP_COLUMN)).as("разрыва до входа нет").isNull();

        poisonWithout(EVENT_ID);

        assertThat(pair(topic()).get(GAP_COLUMN))
                .as("момент разрыва поставлен, хотя обработка сообщения откатилась").isNotNull();
        assertThat(dealFacts()).as("факта отравленной записи нет: легла одна затравка").hasSize(1);
        assertThat(incidentFacts()).as("факта происшествия нет тоже").isEmpty();
        assertThat(pair(topic()).get(HALTED_COLUMN))
                .as("флаг остановки лежит своей транзакцией").isEqualTo(Boolean.TRUE);
        assertThat(continuityClaimable())
                .as("предикат ложен по обеим ветвям сразу").isEqualTo(Boolean.FALSE);
    }
}
