package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.17} — пустой биржевой счёт у сделочного класса
 * отвергается наравне с отсутствующим (.claude/tests/cases/statistics.md).
 *
 * <p>Счёт — ключ сделочного зерна (docs/models/domain/other/StatisticsFact.md
 * §Структура); строка с пустым счётом дала бы зерно агрегата, которого не
 * прочитает никто (docs/rules/statistics-aggregates.md §«Зерно строки»).
 *
 * <p><b>Единица — пустая форма:</b> отсутствующий счёт держит ещё и
 * {@code not null} колонки, пустую строку — только предикат полноты
 * сделочного факта (пояснение у {@code B2.9}). Свой класс — потому, что
 * клетка травит приём ({@link PoisonedReceptionBox}).
 */
class EmptyDealAccountBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-17");
    }

    @Test
    @DisplayName("B2.17 — Пустой биржевой счёт у сделочного класса отвергается наравне с отсутствующим")
    void anEmptyExchangeAccountOfADealClassIsRejectedLikeAnAbsentOne() {
        givenReceptionStateRows();

        poisonUntilSettled(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt), TENANT,
                Bodies.dealClosed("", "S-1"));

        assertReceptionHalted();
    }
}
