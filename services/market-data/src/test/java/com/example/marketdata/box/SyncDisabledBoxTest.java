package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Выключатель тика синка — клетка {@code B2.8} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Выключатель есть ВХОД кейса, и потому у него свой контекст.</b>
 * Выключенный тик ничего не делает ни по расписанию, ни по ручному
 * запуску, а фасад при этом отвечает {@code 202}: он отвечает за ЗАПУСК,
 * а не за работу (.claude/rules/codestyle.md §Джобы;
 * docs/rules/error-handling-policy.md §«Внешняя поверхность»).
 */
class SyncDisabledBoxTest extends MarketDataBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put("instrument-sync.enabled", "false");
        MarketDataSubstrate.register(registry, axes);
    }

    @Test
    @DisplayName("B2.8 — выключатель гасит и расписание, и ручной тик")
    void b2_8_theSwitchSilencesBothTheScheduleAndTheManualTick() {
        connector.answers(ConnectorStub.INSTRUMENTS,
                Feed.array(Feed.instrument(INSTRUMENT, "BTC", "USDT")));
        connector.answers(ConnectorStub.rulesOf(INSTRUMENT), Feed.rules(INSTRUMENT));

        Answer answer = tick(Tick.INSTRUMENT_SYNC);

        assertThat(answer.status()).isEqualTo(202);
        assertThat(connector.count()).isEqualTo(0);
        assertThat(rows.count("instruments")).isEqualTo(0L);
    }
}
