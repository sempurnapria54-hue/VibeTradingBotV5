package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.3}, вторая её запись — отсутствует биржевой счёт у события
 * зерна ПРОИСШЕСТВИЙ (.claude/tests/cases/statistics.md).
 *
 * <p>Довод разведения по классам — у соседа
 * ({@link MissingDealAccountBoxTest}); здесь предъявляется вторая половина
 * предмета: ветвь отказа у подъёма ступени та же, что у терминала сделки, —
 * неполный вход роняет обработку, а не пропускается.
 */
class MissingIncidentAccountBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-3b");
    }

    @Test
    @DisplayName("B2.3 — Отсутствует биржевой счёт у несомого класса: зерно происшествий")
    void anIncidentEventWithoutTheExchangeAccountStopsReception() {
        givenReceptionStateRows();

        poison(envelope(POISON_EVENT, HOLD_RAISED, occurredAt), TENANT,
                Bodies.holdRaisedWithoutAccount());

        assertReceptionHalted();
    }
}
