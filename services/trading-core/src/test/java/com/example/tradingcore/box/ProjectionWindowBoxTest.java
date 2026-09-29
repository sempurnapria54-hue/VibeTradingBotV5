package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Окно чтения листинга у владельца каталога — клетки {@code B10.12} и
 * {@code B10.13}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клеток:</b> предусловие
 * требует инструментов БОЛЬШЕ окна, а окно приезжает конфигурацией. Ставить
 * его общему ящику значило бы менять предмет всем его соседкам, а копить
 * сотни инструментов в стабе — платить временем за то, что задаётся одной
 * осью.
 *
 * <p><b>Своя группа потребителя</b> — довод у шапки
 * {@link TradingCoreSubstrate}.
 */
class ProjectionWindowBoxTest extends TradingCoreBox {

    /** Окно чтения листинга: инструментов у клетки будет больше. */
    private static final String WINDOW = "2";

    /** Ключ окна чтения листинга у тика синка проекций. */
    private static final String LISTING_WINDOW_KEY = "projection-sync.listing-window";

    /** Третий инструмент: им листинг становится длиннее окна. */
    private static final String THIRD_INSTRUMENT = "I3";

    /** Имя третьего инструмента у площадки. */
    private static final String THIRD_EXTERNAL_INSTRUMENT = "SOL-USDT-SWAP";

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-projection-window";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(LISTING_WINDOW_KEY, WINDOW));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B10.12 — каталог сводится обходом окон за курсором")
    void theCatalogueIsProjectedByWalkingWindowsAfterTheCursor() {
        auth.answers(PEER_ACCOUNTS, Feed.emptyArray());
        marketData.answers(PEER_INSTRUMENTS, Feed.array(
                Feed.instrument(INSTRUMENT, EXTERNAL_INSTRUMENT),
                Feed.instrument(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT)));
        marketData.answersWhen(PEER_INSTRUMENTS, "after", SECOND_INSTRUMENT, Feed.array(
                Feed.instrument(THIRD_INSTRUMENT, THIRD_EXTERNAL_INSTRUMENT)));
        answerRules(INSTRUMENT, EXTERNAL_INSTRUMENT);
        answerRules(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);
        answerRules(THIRD_INSTRUMENT, THIRD_EXTERNAL_INSTRUMENT);

        tick(Tick.REGISTRY_PROJECTIONS);

        List<LoggedRequest> windows = marketData.requests(PEER_INSTRUMENTS);
        // Окон два: полное и короткое — короткое последнее, третьего
        // чтения нет.
        assertThat(windows).hasSize(2);
        // Первое окно — от начала листинга: курсора в нём нет, предел —
        // величина конфигурации.
        assertThat(windows.getFirst().queryParameter("after").isPresent()).isFalse();
        assertThat(windows.getFirst().queryParameter("limit").firstValue()).isEqualTo(WINDOW);
        // Второе — за последним инструментом первого.
        assertThat(windows.get(1).queryParameter("after").firstValue()).isEqualTo(SECOND_INSTRUMENT);
        assertThat(windows.get(1).queryParameter("limit").firstValue()).isEqualTo(WINDOW);
        // Сведён весь листинг, а не его первое окно.
        assertThat(rows.count("instruments")).isEqualTo(3L);
        assertThat(rows.row("instruments", "internal_id", THIRD_INSTRUMENT).get("external_rules")).isNotNull();
    }

    @Test
    @DisplayName("B10.13 — окно, чей курсор не сдвинулся, обход заканчивает")
    void aWindowWhoseCursorDidNotMoveEndsTheWalk() {
        auth.answers(PEER_ACCOUNTS, Feed.emptyArray());
        // Владелец курсора не исполняет: на любое окно отдаёт одно и то
        // же полное.
        marketData.answers(PEER_INSTRUMENTS, Feed.array(
                Feed.instrument(INSTRUMENT, EXTERNAL_INSTRUMENT),
                Feed.instrument(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT)));
        answerRules(INSTRUMENT, EXTERNAL_INSTRUMENT);
        answerRules(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);

        tick(Tick.REGISTRY_PROJECTIONS);

        // Второе окно повторило первое — обход закончен, а не зациклен:
        // тик вернулся, и чтений листинга ровно два.
        assertThat(marketData.count(PEER_INSTRUMENTS)).isEqualTo(2);
        assertThat(rows.count("instruments")).isEqualTo(2L);
    }

    /** Заготовка ответа владельца каталога на правила названного инструмента. */
    private void answerRules(String internalId, String externalId) {
        marketData.answers(PEER_INSTRUMENTS + "/" + internalId + "/rules", Feed.instrumentRules(externalId));
    }
}
