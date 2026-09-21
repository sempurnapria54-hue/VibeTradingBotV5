package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.3}, первая её запись — отсутствует биржевой счёт у
 * события СДЕЛОЧНОГО зерна (.claude/tests/cases/statistics.md).
 *
 * <p><b>Кейс живёт двумя классами, и это не дробление, а следствие
 * предмета.</b> Обе его записи отравленные, а поток у слушателя один:
 * положенная второй, запись до обработки просто не дошла бы, и её отрицания
 * сошлись бы по ложной причине. Зёрна при этом разные — сделочное здесь,
 * происшествий у соседнего класса ({@link MissingIncidentAccountBoxTest}), —
 * и предмет кейса именно в том, что ветвь отказа у них ОДНА.
 *
 * <p>Счёт — обязательный ключ обоих зёрен
 * (docs/models/domain/other/StatisticsFact.md §Структура) и радиус, по
 * которому работают остановки (docs/rules/statistics-aggregates.md §«Зерно
 * строки»); без него строка не нашлась бы ни одной выборкой.
 */
class MissingDealAccountBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-3a");
    }

    @Test
    @DisplayName("B2.3 — Отсутствует биржевой счёт у несомого класса: сделочное зерно")
    void aDealEventWithoutTheExchangeAccountStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt), TENANT,
                Bodies.dealClosedWithoutAccount());

        assertReceptionHalted();
    }
}
