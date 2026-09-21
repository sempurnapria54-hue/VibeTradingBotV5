package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.5} — отсутствует тенант: ключ записи пуст
 * (.claude/tests/cases/statistics.md).
 *
 * <p>Тенант приезжает КЛЮЧОМ записи и ничем иным
 * (docs/architecture/data-ownership.md §«Разделение по тенанту»): заголовки и
 * тело у этой записи полны, и всё же вход неполон. Строка с пустым тенантом не
 * появляется ни в одной таблице — иначе она была бы невидима выборке чтения и
 * дала бы собственное зерно агрегата, которого не прочитает никто.
 */
class MissingTenantBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-5");
    }

    @Test
    @DisplayName("B2.5 — Отсутствует тенант: ключ записи пуст")
    void aRecordWithoutAKeyStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt), null,
                Bodies.dealClosed(ACCOUNT, "S-1"));

        assertReceptionHalted();
    }
}
