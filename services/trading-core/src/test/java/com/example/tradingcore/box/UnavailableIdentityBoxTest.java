package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Недоступная служебная идентичность — клетка {@code B12.9}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> точка выдачи
 * токена подменяется на отказывающую, и подавать её переопределением
 * общего ящика значило бы отнять исходящую идентичность у всех его
 * соседок.
 *
 * <p><b>Предмет — ЧТО НЕ ушло.</b> Запрос без заголовка идентичности
 * наружу не уходит вовсе: добыча токена стои́т до вызова, и её отказ
 * прекращает проход. Анонимный вызов к соседу был бы хуже отказа — он
 * ушёл бы и получил {@code 401} уже у него.
 */
class UnavailableIdentityBoxTest extends TradingCoreBox {

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-unavailable-identity";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        // Своя группа потребителя и своя тема — довод у шапки субстрата.
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                "spring.security.oauth2.client.provider.platform.token-uri",
                IdentityStub.stub().refusingTokenUri()));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B12.9 — недоступная служебная идентичность даёт отказ, а не анонимный вызов")
    void anUnavailableServiceIdentityRefusesRatherThanCallsAnonymously() {
        auth.answers(PEER_ACCOUNTS, Feed.array(Feed.account(ACCOUNT, TENANT, "DEMO", "ACTIVE")));
        marketData.answers(PEER_INSTRUMENTS, Feed.emptyArray());
        Integer mark = AppLog.mark();

        tick(Tick.REGISTRY_PROJECTIONS);

        // Запросов к соседям не ушло ни одного: добыча токена стои́т до
        // вызова, и анонимный вызов не состоялся.
        assertThat(auth.count()).isZero();
        assertThat(marketData.count()).isZero();
        // Проход отказал своим классом и записал это.
        assertThat(AppLog.since(mark)).contains("projection sync failed");
        // Сделок в ошибку не уводится: их нет вовсе, и проход их не заводит.
        assertThat(rows.count("deals")).isZero();
        assertThat(rows.count("exchange_accounts")).isZero();
    }
}
