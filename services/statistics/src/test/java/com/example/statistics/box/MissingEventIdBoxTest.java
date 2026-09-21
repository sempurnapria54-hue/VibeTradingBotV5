package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.4} — отсутствует идентичность события
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Ветвь несущая:</b> без идентичности отметки обработанного не
 * существует вовсе (docs/models/domain/other/StatisticsFact.md §«Отметка
 * обработанного»), и повторная доставка задвоила бы факт — то есть исказила
 * бы каждое число, которое из фактов считается.
 *
 * <p>Ключи зерна и ось времени при этом на месте: клетка утверждает именно об
 * идентичности, а не о неполноте вообще. Исход общий всей группе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}).
 */
class MissingEventIdBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-4");
    }

    @Test
    @DisplayName("B2.4 — Отсутствует идентичность события")
    void aMessageWithoutTheEventIdentityStopsReception() {
        givenReceptionStateRows();

        poisonWithout(EVENT_ID);

        assertReceptionHalted();
    }
}
