package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Незаданный адрес соседа — клетка {@code B13.2}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> положение оси
 * {@code neighbours.market-data.base-url} есть предмет кейса, и подавать
 * его переопределением общего ящика значило бы менять предмет всем его
 * соседкам.
 *
 * <p><b>Половины тика независимы, и клетка мерит именно это.</b> Каталог
 * без адреса владельца отказывает, реестр счетов на том же тике
 * отрабатывает: связать их отказы значило бы сделать ненастроенный
 * каталог причиной неизвестности счетов.
 */
class UnconfiguredCatalogueOwnerBoxTest extends TradingCoreBox {

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-unconfigured-catalogue-owner";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        // Своя группа потребителя и своя тема: контексты прогона не
        // закрываются, общее имя группы отняло бы партию темы у соседнего
        // ящика, а общая тема принесла бы её переигрывание
        // (TradingCoreSubstrate §шапка).
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                "neighbours.market-data.base-url", ""));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B13.2 — незаданный адрес соседа отказывает на вызове, а не на подъёме")
    void anUnsetNeighbourAddressRefusesOnTheCallRatherThanOnStartup() {
        auth.answers(PEER_ACCOUNTS, Feed.array(Feed.account(ACCOUNT, TENANT, "DEMO", "ACTIVE")));
        Integer mark = AppLog.mark();

        // Контекст поднялся: клетка вообще исполняется, а её вход —
        // ручной тик, то есть поверхность отвечает.
        tick(Tick.REGISTRY_PROJECTIONS);

        // Половина счетов отработала.
        assertThat(rows.count("exchange_accounts")).isEqualTo(1L);
        // Половина каталога отказала своим классом.
        assertThat(rows.count("instruments")).isZero();
        assertThat(AppLog.since(mark)).contains("Instrument projection sync failed");
        // Поход в чужое окружение не состоялся: стаб владельца каталога
        // запросов не получил ни одного.
        assertThat(marketData.count()).isZero();
    }
}
