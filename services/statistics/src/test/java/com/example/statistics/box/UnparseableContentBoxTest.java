package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.8}, первая её запись — содержимое не разбирается как
 * документ (.claude/tests/cases/statistics.md).
 *
 * <p><b>Кейс живёт двумя классами по тому же доводу, что и {@code B2.3}:</b>
 * обе его записи отравленные, а поток у слушателя один. Звено у них при этом
 * разное — приведение тела к документу здесь, чтение числового операнда у
 * соседа ({@link UnparseableNumberBoxTest}), — и подменять одно другим клетка
 * не вправе.
 *
 * <p><b>Тело оборвано посреди объекта, а не подано скаляром</b>
 * ({@link Bodies#notADocument()}): скаляр разбирается как документ успешно, и
 * его ветвь — свой пробел покрытия ({@code G5} документа кейсов), а не эта.
 */
class UnparseableContentBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-8a");
    }

    @Test
    @DisplayName("B2.8 — Содержимое не разбирается как документ")
    void anUnparseableContentStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt), TENANT, Bodies.notADocument());

        assertReceptionHalted();
    }
}
