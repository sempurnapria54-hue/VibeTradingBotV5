package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Окно выборки нетерминальных сделок у прохода сопровождения — клетка
 * {@code B2.2}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> предусловие
 * требует сделок БОЛЬШЕ окна, а окно приезжает конфигурацией
 * ({@code DealOrchestratorProperties#batchSize}). Ставить его общему ящику
 * значило бы менять предмет всем его соседкам, а заводить сотню сделок
 * тропой ящика — платить временем за то, что задаётся одной осью.
 *
 * <p><b>Сделка ставится ТРОПОЙ ЯЩИКА — тиком отбора входа</b>, и счетов
 * поэтому два: гейт отбора закрывает счёт целиком, как только на нём
 * заведена сделка ({@code B1.3}).
 *
 * <p><b>Первая сделка держится нетерминальной живой заявкой.</b> Без неё
 * обработчик ошибочного состояния довёл бы её до аварийного терминала тем
 * же проходом, и «повтор тика даёт тот же набор» стало бы утверждением о
 * том, что популяция изменилась сама. Живая заявка ставится прямой
 * записью: её пишет команда площадке — предмет группы {@code B3}.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class DealPassWindowBoxTest extends TradingCoreBox {

    /** Окно выборки: сделок у клетки будет больше него. */
    private static final String WINDOW = "1";

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-deal-pass-window";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.PASS_WINDOW_KEY, WINDOW));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B2.2 — окно выборки ограничено, не попавшее подбирает следующий тик")
    void dealsBeyondTheWindowArePickedUpByTheNextTick() {
        provisionTwoPairs();
        openDeals();
        // Порядок выборки детерминирован идентификатором: в окно в один
        // объект попадает сделка с меньшим ключом.
        assertThat(dealIdOn(INSTRUMENT)).isLessThan(dealIdOn(SECOND_INSTRUMENT));
        putLiveEntryOrder(dealIdOn(INSTRUMENT));

        tick(Tick.DEAL_ORCHESTRATOR);

        // Прошла ровно одна сделка: сборка контекста второй не начиналась,
        // и следа исполнения у неё нет ни одного.
        Integer afterFirst = marketData.count(featuresPath(INSTRUMENT));
        assertThat(afterFirst).isPositive();
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isZero();
        assertThat(executionOwners()).containsExactly(dealIdOn(INSTRUMENT));

        tick(Tick.DEAL_ORCHESTRATOR);

        // Повтор тика даёт ТОТ ЖЕ набор: популяция не изменилась, и окно
        // отбирает по тому же ключу. Число чтений первой пары при этом
        // РАСТЁТ — по ним и видно, что второй проход взял её же.
        assertThat(marketData.count(featuresPath(INSTRUMENT))).isGreaterThan(afterFirst);
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isZero();
        assertThat(executionOwners()).containsExactly(dealIdOn(INSTRUMENT));

        // Первая ушла из популяции — терминальные статусы ставятся прямой
        // записью (их рёбра пишет полный выход, предмет групп B3 и B5).
        rows.put("update deals set status = 'CLOSED' where id = ?", dealIdOn(INSTRUMENT));
        tick(Tick.DEAL_ORCHESTRATOR);

        // Не попавшее подобрал следующий тик.
        assertThat(marketData.count(featuresPath(SECOND_INSTRUMENT))).isPositive();
        assertThat(executionOwners()).contains(dealIdOn(SECOND_INSTRUMENT));
    }

    // ------------------------------------------------------------------
    // Предусловия клетки
    // ------------------------------------------------------------------

    /** Два счёта и два инструмента проекциями тропой ящика. */
    private void provisionTwoPairs() {
        Map<String, String> catalogue = new LinkedHashMap<>();
        catalogue.put(INSTRUMENT, EXTERNAL_INSTRUMENT);
        catalogue.put(SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT);
        provision(List.of(ACCOUNT, SECOND_ACCOUNT), catalogue);
    }

    /** По сделке на каждой паре — одним тиком отбора входа. */
    private void openDeals() {
        answerFor(ACCOUNT, INSTRUMENT);
        answerFor(SECOND_ACCOUNT, SECOND_INSTRUMENT);
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(Definitions.withEntryOnPhase("S1", ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND));
        activate(Definitions.withEntryOnPhase("S2", SECOND_ACCOUNT, SECOND_INSTRUMENT,
                MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(2L);
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Связка фич момента и снимок средств счёта: без них проход не идёт. */
    private void answerFor(String accountInternalId, String instrumentInternalId) {
        marketData.answers(featuresPath(instrumentInternalId),
                Feed.features(MarketPhase.Type.BULL_TREND.name()));
        connector.answers("/api/v1/accounts/" + accountInternalId + "/balance", balanceBody());
    }

    /** Путь чтения связки фич момента у владельца рыночных данных. */
    private String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    /**
     * Сделки, у которых проход завёл хоть одну строку исполнения, без
     * повторов и в порядке ключей.
     *
     * <p>Ими наблюдается ЧИСЛО пройденных сделок: строку исполнения
     * заводит работа обработчика, и сделка, до которой окно не дошло, её
     * не имеет ни одной. Ассерт прямой по базе — поверхности у строк
     * исполнения нет.
     */
    private List<Long> executionOwners() {
        return rows.select("""
                select distinct deal_id from deal_system_action_states order by deal_id asc
                """).stream().map(row -> ((Number) row.get("deal_id")).longValue()).toList();
    }

    /** Ключ сделки на названном инструменте. */
    private Long dealIdOn(String instrumentInternalId) {
        return ((Number) rows.row("deals", "instrument_id", instrumentId(instrumentInternalId))
                .get("id")).longValue();
    }

    /** Живая входная заявка сделки прямой записью — довод в шапке класса. */
    private void putLiveEntryOrder(Long dealId) {
        Long trancheId = ((Number) rows.rowsWhere("deal_tranches", "deal_id", dealId).getFirst()
                .get("id")).longValue();
        rows.put("""
                insert into orders (deal_id, deal_tranche_id, internal_id, status, type, side, size)
                values (?, ?, ?, 'ACTIVE', 'ENTRY', 'BUY', 1)
                """, dealId, trancheId, "live-entry-" + trancheId);
    }

    /** Снимок средств моментом прогона: возраст ставится В ДАННЫХ. */
    private String balanceBody() {
        String moment = OffsetDateTime.now(ZoneOffset.UTC).toString();
        return """
                {
                  "externalUpdatedAt": "%s",
                  "externalTotalEquity": "100000",
                  "externalAdjustedEquity": "100000",
                  "externalAvailableEquity": "100000",
                  "balances": [
                    {
                      "externalCurrency": "USDT",
                      "externalUpdatedAt": "%s",
                      "externalEquity": "100000",
                      "externalCashBalance": "100000",
                      "externalAvailableBalance": "100000",
                      "externalFrozenBalance": "0"
                    }
                  ]
                }
                """.formatted(moment, moment);
    }
}
