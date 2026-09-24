package com.example.tradingcore.unit.safety;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Базовые сборки групп документа `.claude/tests/cases/trading-core-safety.md`.
 *
 * <p><b>Субстрата у предмета нет вовсе:</b> ни контейнеров, ни контекста
 * каркаса — ребро подъёма, координатор, сервис блокировки, сервис отчёта,
 * реакция, гейт прохода, ручная поверхность и счётчик серии
 * конструируются {@code new}, а подменяются ровно те коллабораторы, у
 * которых есть ввод-вывод (§«Чем достаются выходы» того же документа).
 *
 * <p><b>Состояние собирается настоящими полями доменных моделей</b>, а не
 * подменёнными предикатами (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»): живой риск эпизода вытекает из его статуса и наблюдённого
 * размера, накопленная издержка финансирования — из эпизодов сделки,
 * стоящая ступень — из поля своей строки.
 */
final class SafetyFixture {

    /** Числовая идентичность биржевого счёта базовой сборки. */
    static final Long ACCOUNT_ID = 41L;

    /** Числовая идентичность инструмента базовой сборки. */
    static final Long INSTRUMENT_ID = 77L;

    /** Межсервисная идентичность счёта: она едет содержимым факта. */
    static final String ACCOUNT_INTERNAL_ID = "EXA-0000000041";

    /** Межсервисная идентичность инструмента: она же едет содержимым факта. */
    static final String INSTRUMENT_INTERNAL_ID = "INS-0000000077";

    /** Тенант-владелец счёта: ключ партиции конверта события. */
    static final String TENANT_ID = "TEN-0000000001";

    /** Биржевая идентичность инструмента: по ней читается площадка. */
    static final String INSTRUMENT_EXTERNAL_ID = "ETH-USDT-SWAP";

    /** Машинный код причины, с которым приходят сигналы сборок. */
    static final String CODE = "TEST_REASON";

    /** Названный принципал хода: актор факта берётся у поставщика контекста. */
    static final String ACTOR = "holder@example";

    private SafetyFixture() {
    }

    /** Счёт в рабочем состоянии без стоящей ступени. */
    static ExchangeAccount account() {
        return account(ExchangeAccount.SafetyRung.ACTIVE);
    }

    /** Тот же счёт со стоящей ступенью названного уровня. */
    static ExchangeAccount account(ExchangeAccount.SafetyRung rung) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setTenantId(TENANT_ID);
        account.setExchangeCode("OKX");
        account.setStatus(ExchangeAccount.Status.ACTIVE);
        account.setSafetyRung(rung);
        return account;
    }

    /** Инструмент в рабочем состоянии. */
    static Instrument instrument() {
        return instrument(Instrument.Status.ACTIVE);
    }

    /** Тот же инструмент в названном онбординговом статусе. */
    static Instrument instrument(Instrument.Status status) {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        instrument.setExternalId(INSTRUMENT_EXTERNAL_ID);
        instrument.setStatus(status);
        return instrument;
    }

    /** Строка пары «счёт, инструмент» с названной ступенью её лестницы. */
    static AccountInstrumentState pairState(Instrument.SafetyRung rung) {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setId(5L);
        state.setExchangeAccountId(ACCOUNT_ID);
        state.setInstrumentId(INSTRUMENT_ID);
        state.setSafetyRung(rung);
        return state;
    }

    /** Контекст объекта радиуса пары: счёт и инструмент. */
    static DealContext pairContext() {
        return DealContext.builder().exchangeAccount(account()).instrument(instrument()).build();
    }

    /** Контекст прохода по сделке пары — так приходит автоматический сигнал. */
    static DealContext pairContext(Deal deal) {
        return DealContext.builder().deal(deal).exchangeAccount(account()).instrument(instrument()).build();
    }

    /** Контекст объекта счётного радиуса: инструмента нет по построению. */
    static DealContext accountContext() {
        return DealContext.builder().exchangeAccount(account()).build();
    }

    /** Нетерминальная сделка названной идентичности. */
    static Deal deal(Long id) {
        Deal deal = new Deal();
        deal.setId(id);
        deal.setInternalId("DEA-" + id);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setTranches(new ArrayList<>());
        deal.setPositions(new ArrayList<>());
        return deal;
    }

    /**
     * Сделка с живым эпизодом: статус активен и наблюдённый размер
     * положителен — предикат живого риска считается, а не подменяется.
     */
    static Deal dealWithLiveRisk(Long id) {
        Deal deal = deal(id);
        deal.setPositions(new ArrayList<>(List.of(position(Position.Status.ACTIVE,
                new BigDecimal("1.5"), null))));
        return deal;
    }

    /** Эпизод позиции настоящими полями. */
    static Position position(Position.Status status, BigDecimal externalSize,
                             BigDecimal externalFundingCost) {
        Position position = new Position();
        position.setId(900L);
        position.setStatus(status);
        position.setExternalSize(externalSize);
        position.setExternalFundingCost(externalFundingCost);
        return position;
    }

    /** Транш с ногами: перечень ног снимка собирается их обходом. */
    static DealTranche tranche(Long id, Long... orderIds) {
        DealTranche tranche = new DealTranche();
        tranche.setId(id);
        tranche.setOrders(new ArrayList<>(Arrays.stream(orderIds).map(SafetyFixture::order).toList()));
        return tranche;
    }

    /** Нога транша. */
    static Order order(Long id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }

    /** Изменяемый перечень — популяция каскада отдаётся выборкой. */
    static List<Deal> deals(Deal... deals) {
        return new ArrayList<>(Arrays.asList(deals));
    }
}
