package com.example.tradingcore.unit.calc;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.config.PnlReconciliationProperties;
import com.example.tradingcore.domain.command.DealContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Общая сборка предмета {@code trading-core-calc} — расчётного и
 * резолверного слоя ядра (`.claude/tests/cases/trading-core-calc.md`
 * §«Чем достаются выходы»).
 *
 * <p><b>Субстрата у предмета нет:</b> ни контейнеров, ни контекста
 * Spring, ни базы. Классы конструируются {@code new}, конфигурация
 * собирается обычным объектом, доменные модели — <b>настоящими
 * полями</b>: {@code positionObserved}, {@code billsWindowLowerBound} и
 * {@code hasEntryFill} обязаны считаться, а не отвечать подменённым
 * значением (`.claude/rules/codestyle.md` §«Тесты доменных моделей»).
 *
 * <p><b>Мок здесь ровно один, и он граница</b> — журнал происшествий у
 * писателя признаков; собирается он своими группами, а не тут.
 */
final class CalcFixture {

    /** Код площадки сделки — ключ секции контура. */
    static final String EXCHANGE = "OKX";

    /** Расчётная валюта инструмента — единственный авторитет валюты результата. */
    static final String SETTLE = "USDT";

    /** Валюта движения, не являющаяся расчётной. */
    static final String FOREIGN = "BTC";

    /** Сырой тип движения, покрытый экономикой сделки. */
    static final String TRADE_TYPE = "2";

    /** Момент, до которого доказано покрытие; непустота — конъюнкт обязанности сверки. */
    static final OffsetDateTime COVERAGE_PROVEN = OffsetDateTime.parse("2026-09-06T10:00:00Z");

    /** Момент, по который добыты движения; непустота — конъюнкт обязанности сверки. */
    static final OffsetDateTime BILLS_FETCHED = OffsetDateTime.parse("2026-09-06T11:00:00Z");

    private CalcFixture() {
    }

    // ------------------------------------------------------------------
    // Конфигурация
    // ------------------------------------------------------------------

    /** Контур площадки сделки с названными исключениями сверки. */
    static ExchangeContourProperties contourProperties(String... exclusions) {
        ExchangeContourProperties properties = new ExchangeContourProperties();
        ExchangeContourProperties.Contour contour = new ExchangeContourProperties.Contour();
        contour.setReconciliationExclusions(new ArrayList<>(Arrays.asList(exclusions)));
        properties.setExchanges(new LinkedHashMap<>(Map.of(EXCHANGE, contour)));
        return properties;
    }

    /** Конфигурация без секции площадки сделки: резолв отдаёт пустой контур. */
    static ExchangeContourProperties contourPropertiesWithoutSection() {
        return new ExchangeContourProperties();
    }

    /** Три числа допуска, заданные кейсом явно. */
    static PnlReconciliationProperties tolerance(String relativeShare, String omissionMultiplier,
                                                 String floor) {
        PnlReconciliationProperties properties = new PnlReconciliationProperties();
        properties.setRelativeShare(new BigDecimal(relativeShare));
        properties.setOmissionMultiplier(new BigDecimal(omissionMultiplier));
        properties.setFloor(new BigDecimal(floor));
        return properties;
    }

    /** Рабочие числа допуска конфигурации. */
    static PnlReconciliationProperties workingTolerance() {
        return tolerance("0.0005", "1.5", "0.05");
    }

    // ------------------------------------------------------------------
    // Эпизоды позиции
    // ------------------------------------------------------------------

    /** Эпизод с добытой записью закрытия: число, его валюта и сырой тип закрытия. */
    static Position closedEpisode(String net, String closeType) {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        episode.setExternalRealizedProfit(decimal(net));
        episode.setExternalResultCurrency(SETTLE);
        episode.setExternalCloseType(closeType);
        return episode;
    }

    /** Эпизод со штатным сырым типом закрытия. */
    static Position closedEpisode(String net) {
        return closedEpisode(net, "1");
    }

    /** Эпизод, чья запись закрытия не добыта: числа нет, и валюты тоже. */
    static Position episodeWithoutCloseRecord() {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        return episode;
    }

    /** Четыре биржевых числа эпизода — правые стороны пар сверки. */
    static Position withExchangeNumbers(Position episode, String gross, String fee, String funding,
                                        String penalty) {
        episode.setExternalRealizedProfitGross(decimal(gross));
        episode.setExternalFee(decimal(fee));
        episode.setExternalFundingCost(decimal(funding));
        episode.setExternalLiquidationPenalty(decimal(penalty));
        return episode;
    }

    /** Эпизод сверки: net 9, валовой результат 10, комиссия −1, прочие нули. */
    static Position reconciledEpisode() {
        return withExchangeNumbers(closedEpisode("9"), "10", "-1", "0", "0");
    }

    // ------------------------------------------------------------------
    // Строки разбивки движений
    // ------------------------------------------------------------------

    /** Строка движения расчётной валюты экономической категории. */
    static DealCashFlow flow(DealCashFlow.CashFlowCategory category, String amount) {
        return flow(category, SETTLE, amount);
    }

    /** Строка движения названной валюты. */
    static DealCashFlow flow(DealCashFlow.CashFlowCategory category, String ccy, String amount) {
        DealCashFlow cashFlow = new DealCashFlow();
        cashFlow.setCategory(category);
        cashFlow.setCcy(ccy);
        cashFlow.setAmount(decimal(amount));
        cashFlow.setExternalType(TRADE_TYPE);
        cashFlow.setRateStatus(DealCashFlow.RateStatus.NOT_REQUIRED);
        return cashFlow;
    }

    /** Та же строка с названным сырым типом источника. */
    static DealCashFlow withType(DealCashFlow flow, String externalType, String externalSubType) {
        flow.setExternalType(externalType);
        flow.setExternalSubType(externalSubType);
        return flow;
    }

    /** Та же строка со статусом курса и самим курсом. */
    static DealCashFlow withRate(DealCashFlow flow, DealCashFlow.RateStatus status, String rate) {
        flow.setRateStatus(status);
        flow.setAppliedRate(decimal(rate));
        return flow;
    }

    /** Комиссионное эхо на торговой строке — поле явной комиссионной компоненты. */
    static DealCashFlow withFeeComponent(DealCashFlow flow, String externalFee) {
        flow.setExternalFee(decimal(externalFee));
        return flow;
    }

    // ------------------------------------------------------------------
    // Сделка
    // ------------------------------------------------------------------

    /**
     * Вошедшая сделка: один транш с налитой входной ногой, то есть
     * {@code positionObserved()} истинен <b>по составу</b>, а не
     * подменённым предикатом.
     */
    static Deal enteredDeal(Position... episodes) {
        Deal deal = baseDeal(episodes);
        deal.setTranches(List.of(filledTranche()));
        return deal;
    }

    /** Вошедшая сделка, чей единственный транш несёт названные ноги. */
    static Deal enteredDealWithLegs(List<Order> legs, Position... episodes) {
        Deal deal = baseDeal(episodes);
        DealTranche tranche = filledTranche();
        tranche.setOrders(new ArrayList<>(legs));
        deal.setTranches(List.of(tranche));
        return deal;
    }

    /** Сделка, не входившая: транш без налива, причина входа не RECOVERY. */
    static Deal dealWithoutEntry(Position... episodes) {
        Deal deal = baseDeal(episodes);
        DealTranche tranche = new DealTranche();
        tranche.setId(2L);
        deal.setTranches(List.of(tranche));
        return deal;
    }

    /** Сделка, заведённая восстановлением: налитых ног нет, наблюдение даёт второй дизъюнкт. */
    static Deal recoveredDeal(Position... episodes) {
        Deal deal = dealWithoutEntry(episodes);
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        return deal;
    }

    private static Deal baseDeal(Position... episodes) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setPositions(List.of(episodes));
        deal.setCoverageProvenThrough(COVERAGE_PROVEN);
        deal.setBillsFetchedThrough(BILLS_FETCHED);
        deal.setBillsWindowBegin(BILLS_FETCHED.minusDays(1));
        deal.setPlannedRiskAmount(new BigDecimal("10"));
        return deal;
    }

    private static DealTranche filledTranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(2L);
        tranche.setEntryFilled(new BigDecimal("1"));
        tranche.setOrders(new ArrayList<>());
        return tranche;
    }

    /**
     * Нога входа с наливом: множество омиссионного члена — предикат
     * ВЗЯТОГО риска, а ставка комиссии восстанавливается обращением
     * закрытой формы сайзинга у дома чисел риска.
     */
    static Order entryLeg(String plannedRisk, String plannedSize, String fill) {
        Order leg = new Order();
        leg.setId(3L);
        leg.setType(Order.Type.ENTRY);
        leg.setStatus(Order.Status.COMPLETED);
        leg.setPlannedRiskAmount(decimal(plannedRisk));
        leg.setPlannedEntryPrice(new BigDecimal("3000"));
        leg.setPlannedStopPrice(new BigDecimal("2900"));
        leg.setPlannedSizeContracts(decimal(plannedSize));
        leg.setPlannedContractValue(new BigDecimal("0.1"));
        leg.setAccumulatedFillSize(decimal(fill));
        return leg;
    }

    /** Нога входа, чья ожидаемая round-trip комиссия равна единице расчётной валюты. */
    static Order unitFeeEntryLeg() {
        return entryLeg("11", "1", "1");
    }

    // ------------------------------------------------------------------
    // Контекст прохода
    // ------------------------------------------------------------------

    /** Контекст прохода: расчётная валюта {@link #SETTLE}, оба признака полноты истинны. */
    static DealContext context(Deal deal, List<DealCashFlow> cashFlows) {
        return contextBuilder(deal, cashFlows, SETTLE).build();
    }

    /** Контекст с названной расчётной валютой инструмента. */
    static DealContext context(Deal deal, List<DealCashFlow> cashFlows, String settleCurrency) {
        return contextBuilder(deal, cashFlows, settleCurrency).build();
    }

    /** Строитель контекста — для кейсов, меняющих признаки полноты либо код площадки. */
    static DealContext.DealContextBuilder contextBuilder(Deal deal, List<DealCashFlow> cashFlows,
                                                         String settleCurrency) {
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account(EXCHANGE))
                .instrument(instrument(settleCurrency))
                .cashFlows(new ArrayList<>(cashFlows))
                .graphComplete(true)
                .flowsComplete(true);
    }

    /** Биржевой счёт с названным кодом площадки. */
    static ExchangeAccount account(String exchangeCode) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(4L);
        account.setExchangeCode(exchangeCode);
        account.setTenantId("tn-0001");
        return account;
    }

    /** Инструмент с названной расчётной валютой; пустая означает нерезолвимость. */
    static Instrument instrument(String settleCurrency) {
        Instrument instrument = new Instrument();
        instrument.setId(5L);
        instrument.setExternalSettlementCurrency(settleCurrency);
        return instrument;
    }

    /** Пустая строка оставляет величину пустой — состояние, а не ноль. */
    static BigDecimal decimal(String value) {
        return value.isEmpty() ? null : new BigDecimal(value);
    }
}
