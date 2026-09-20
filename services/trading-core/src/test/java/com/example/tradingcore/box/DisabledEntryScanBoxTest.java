package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Выключатель отбора входа — клетка {@code B1.13}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> положение оси
 * {@code entry-scanner.enabled} есть предмет кейса. Расписание у прогона
 * глушится ВЫРАЖЕНИЕМ такта, а не выключателем, — иначе этой клетке
 * нечего было бы мерить ({@link TradingCoreSubstrate} §шапка).
 *
 * <p><b>Предусловия входа поставлены ЦЕЛИКОМ, и это несущее.</b>
 * Выключенный тик, которому и входить было бы некуда, зелен при любом
 * поведении выключателя: клетка мерит разницу между «нечего делать» и
 * «делать запрещено», и потому ставит ровно те предусловия, на которых
 * {@code B1.1} сделку заводит.
 *
 * <p><b>Ответ фасада — о ЗАПУСКЕ, а не об исходе работы</b>
 * (docs/rules/error-handling-policy.md): {@code 202} приходит и на
 * выключенном тике, и единственный его смысл — что поверхность приняла
 * вызов.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}: свежая группа читает тему с
 * начала, и определение соседнего класса прогона приехало бы сюда копией,
 * которой клетка не заводила.
 */
class DisabledEntryScanBoxTest extends TradingCoreBox {

    /** Определение клетки: условие входа у него выполнено. */
    private static final String DEFINITION = "S1";

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-disabled-entry-scan";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.ENTRY_ENABLED_KEY, "false"));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B1.13 — выключатель гасит и расписание, и ручной тик")
    void theSwitchSilencesTheManualTickAsWellAsTheSchedule() {
        Map<String, String> catalogue = new LinkedHashMap<>();
        catalogue.put(INSTRUMENT, EXTERNAL_INSTRUMENT);
        provision(List.of(ACCOUNT), catalogue);
        marketData.answers(PEER_INSTRUMENTS + "/" + INSTRUMENT + "/features",
                Feed.features(MarketPhase.Type.BULL_TREND.name()));
        connector.answers(PEER_SERVER_TIME, Feed.serverTime("2026-09-20T10:00:00Z"));
        activate(Definitions.withEntryOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND));

        Answer answer = tick(Tick.ENTRY_SCANNER);

        // Фасад принял запуск: выключатель гасит РАБОТУ, а не поверхность.
        assertThat(answer.status()).isEqualTo(202);
        assertThat(rows.count("deals")).isZero();
        assertThat(rows.count("outbox_events")).isZero();
        // Обращений к соседям нет ни одного: выключатель стои́т до выборки
        // счетов (.claude/rules/codestyle.md §Джобы).
        assertThat(marketData.count()).isZero();
        assertThat(connector.count()).isZero();
    }
}
