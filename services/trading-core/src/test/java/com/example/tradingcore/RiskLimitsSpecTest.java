package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.PriceMode;
import com.example.strategy.engine.calc.ResolvedStopLossPrice;
import com.example.strategy.engine.calc.SizeMode;
import com.example.strategy.engine.calc.StrategyPricePurpose;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Потолки риска — исполнимая форма docs/spec/risk-limits.json. Каждый тест
 * назван примером спеки, из которого взяты и состояние, и ожидание.
 *
 * <p><b>Состояние собирается настоящими полями</b> — ногами траншей с их
 * наливом, эпизодом позиции, защитами с их уровнями, — а не подменёнными
 * предикатами: подменённый предикат проверял бы модель, которой в проде не
 * существует (.claude/rules/codestyle.md §«Тесты доменных моделей»). Мок
 * стои́т только на границах хранилища — у них своя проверка.
 *
 * <p><b>Величины базы риска (первое наблюдение и ход вниз) сюда не
 * входят:</b> их пишут приземление снимка и финализация, и первую половину
 * держит {@code BalanceRefreshTest}.
 */
class RiskLimitsSpecTest {

    private static final Long ACCOUNT_ID = 3L;
    private static final Long INSTRUMENT_ID = 7L;
    private static final String TENANT = "tn-0001";
    private static final BigDecimal ANCHOR = new BigDecimal("3000");

    private final InstrumentExternalRulesDataService rulesDataService =
            mock(InstrumentExternalRulesDataService.class);
    private final AccountInstrumentStateDataService pairStateDataService =
            mock(AccountInstrumentStateDataService.class);
    private final TenantRiskAppetiteDataService appetiteDataService =
            mock(TenantRiskAppetiteDataService.class);

    private final RiskValidator validator =
            new RiskValidator(rulesDataService, pairStateDataService, appetiteDataService);

    /**
     * Умолчания границ хранилища — рабочий инструмент и рабочая пара.
     * Стоя́т ЗДЕСЬ, а не в сборке контекста: сборка вызывается аргументом
     * проверки, то есть ПОСЛЕ тела теста, и затирала бы стабы, которыми
     * тест как раз и задаёт свой предмет.
     */
    @BeforeEach
    void givenWorkingBoundaries() {
        when(rulesDataService.findByInstrumentId(any(), any()))
                .thenReturn(Optional.of(rules("0.1", "1", "1", "0.0005")));
        when(pairStateDataService.getRequiredByPair(any(), any()))
                .thenReturn(pairState(Instrument.SafetyRung.ACTIVE));
    }

    // --- живой риск и его слагаемые ---------------------------------------

    /** Пример «живой риск: неисполненная доля ноги плюс живой эпизод до стопа». */
    @Test
    void liveRiskAddsTheUnfilledLegShareToTheEpisodeRiskAtStop() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(
                List.of(entryLeg(Order.Status.ACTIVE, "100", "100", "30")),
                List.of(protection("2910")))));
        deal.setPositions(List.of(episode("10", ANCHOR)));

        // liveRiskNow = 70 + 92.955 = 162.955 против потолка 1 % x 10000 = 100
        RiskValidationResult result = validator.validate(weakeningAction(), context(deal));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED);
    }

    /** Пример «стоп за безубытком: живое слагаемое гаснет, плановый риск живой ноги — нет». */
    @Test
    void stopBeyondBreakevenExtinguishesItsOwnSummandOnly() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(
                List.of(entryLeg(Order.Status.ACTIVE, "100", "100", "30")),
                List.of(protection("3010")))));
        deal.setPositions(List.of(episode("10", ANCHOR)));

        // liveRiskNow = 70 + 0 = 70 <= 100: оба одновременных потолка проходят
        RiskValidationResult result = validator.validate(weakeningAction(), context(deal));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED);
    }

    /** Пример «вырожденный знаменатель даёт ноль, а не деление». */
    @Test
    void degenerateDenominatorYieldsZeroInsteadOfDivision() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(
                List.of(entryLeg(Order.Status.ACTIVE, "0", "0", "0")),
                List.of(protection("2910")))));

        RiskValidationResult result = validator.validate(weakeningAction(), context(deal));

        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOWED);
    }

    // --- поактный потолок и разведение двух его отказов --------------------

    /** Пример «поактное неравенство: нога под бюджет проходит, вдвое большая — нет». */
    @Test
    void actWithinThePerActionBudgetPasses() {
        RiskValidationResult result = validator.validate(
                entryAction("10", ANCHOR, "2910"), context(emptyDeal()));

        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOWED);
    }

    /** Пример «риск акта вдвое выше поактного бюджета отвергается». */
    @Test
    void actTwiceThePerActionBudgetIsRejected() {
        RiskValidationResult result = validator.validate(
                entryAction("20", ANCHOR, "2910"), context(emptyDeal()));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    /**
     * Превышение на РАЗМЕРЕ МИНИМАЛЬНОГО ЛОТА — свой код: ветвь подъёма до
     * минимума потолком не ограничена вовсе, и один код на оба исхода делал
     * бы карв-аут неразрешимым
     * (docs/components/models/RiskCheckResult.md).
     */
    @Test
    void indivisibleMinimumLotOverTheBudgetGetsItsOwnCode() {
        when(rulesDataService.findByInstrumentId(any(), any()))
                .thenReturn(Optional.of(rules("0.1", "1", "20", "0.0005")));

        RiskValidationResult result = validator.validate(
                entryAction("20", ANCHOR, "2910"), context(emptyDeal()));

        assertThat(codes(result)).contains(RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET)
                .doesNotContain(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    // --- кумулятивный и катастрофический потолки ---------------------------

    /** Пример «кумулятивный потолок считает принятый сделкой риск вместе с риском акта». */
    @Test
    void cumulativeCeilingCountsTheDealRiskTakenTogetherWithTheAct() {
        Deal deal = deal(new BigDecimal("250"), new BigDecimal("10000"));

        // 250 + 92.955 > 3 x 1 % x 10000 = 300
        RiskValidationResult result = validator.validate(
                entryAction("10", ANCHOR, "2910"), context(deal));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
    }

    /**
     * Пример «первый вход выше катастрофического потолка отвергается на
     * создании: ни живых ног, ни эпизода, ни защиты ещё нет».
     */
    @Test
    void firstEntryAboveTheCatastrophicCeilingIsRejectedAtCreation() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        StrategyDetail detail = detail("1", "3", "1", "100");

        // нотинал акта 10000 против потолка 0.5 % x 10000 x 100 = 5000
        RiskValidationResult result = validator.validate(
                entryAction("100", ANCHOR, "2910"), context(deal, detail, appetite("0.5", "3")));

        assertThat(codes(result)).contains(RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    /**
     * Пример «частично налитая живая нога не задваивает свой филл: нотинал
     * считается неисполненной долей плюс живым эпизодом».
     */
    @Test
    void partiallyFilledLegDoesNotDoubleCountItsFill() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(
                List.of(entryLeg(Order.Status.PARTIALLY_COMPLETED, "100", "100", "30")),
                List.of(protection("2910")))));
        deal.setPositions(List.of(episode("30", ANCHOR)));
        StrategyDetail detail = detail("1", "20", "100", "300");

        // 21000 + 9000 = 30000 ровно в потолок 1 % x 10000 x 300
        RiskValidationResult result = validator.validate(weakeningAction(), context(deal, detail, appetite()));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    /**
     * Пример «добор поверх ровно заполненного потолка отвергается, хотя
     * существующая экспозиция в него укладывается».
     */
    @Test
    void addOnOverAnExactlyFilledCeilingIsRejected() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(
                List.of(entryLeg(Order.Status.ACTIVE, "100", "70", "0")),
                List.of(protection("2910")))));
        deal.setPositions(List.of(episode("30", ANCHOR)));
        StrategyDetail detail = detail("1", "20", "100", "300");

        // 21000 + 9000 = 30000 = потолок; слагаемое акта 3000 его переполняет
        RiskValidationResult result = validator.validate(
                entryAction("10", ANCHOR, "2910"), context(deal, detail, appetite()));

        assertThat(codes(result)).contains(RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    /**
     * Пример «вход после частичного выхода: исполнившаяся нога в нотинал не
     * идёт, экспозицию несёт ужавшийся эпизод».
     */
    @Test
    void completedLegLeavesTheNotionalToTheShrunkEpisode() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(
                List.of(entryLeg(Order.Status.COMPLETED, "100", "100", "100")),
                List.of(protection("2910")))));
        deal.setPositions(List.of(episode("60", ANCHOR)));
        StrategyDetail detail = detail("1", "20", "100", "300");

        // живых ног нет: 0 + 18000 + слагаемое акта 3000 <= 30000
        RiskValidationResult result = validator.validate(
                entryAction("10", ANCHOR, "2910"), context(deal, detail, appetite()));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    // --- незаданные числа отказывают вычислением ---------------------------

    /** Пример «незаявленный множитель отказывает, а не пропускает действие». */
    @Test
    void undeclaredCatastrophicMultiplierRefusesInsteadOfPassing() {
        StrategyDetail detail = detail("1", "20", "1", null);

        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(emptyDeal(), detail, appetite()));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    /**
     * Пример «незаданный максимальный риск на сделку отказывает обоими
     * потолками, которые на нём стоят»: до неравенств проверка не доходит —
     * число читается входным гейтом.
     */
    @Test
    void undeclaredMaxRiskPerDealRefusesBeforeTheInequalities() {
        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(emptyDeal(), detail(), appetite(null, "3")));

        assertThat(codes(result)).containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    /** Незаданный порог серии убытков отвергает своим кодом — энфорсера остановки не существует. */
    @Test
    void undeclaredLossStreakLimitRefusesWithItsOwnCode() {
        Tenant appetite = appetite("1", "3");
        appetite.setGlobalConsecutiveLossLimit(null);

        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(emptyDeal(), detail(), appetite));

        assertThat(codes(result)).containsExactly(RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED);
    }

    /**
     * Пример «нога сделки в контекст не попала: потолки, стоящие на графе,
     * отказывают, а не разрешают» — неполный граф отвергается fail-fast, до
     * единой проверки.
     */
    @Test
    void incompleteGraphRefusesFailFast() {
        DealContext context = contextBuilder(emptyDeal(), detail(), appetite()).graphComplete(false).build();

        RiskValidationResult result = validator.validate(entryAction("1", ANCHOR, "2910"), context);

        assertThat(codes(result)).containsExactly(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
    }

    // --- база: снимок сделки против живой базы счёта ------------------------

    /** Пример «первое действие сделки: снимка базы ещё нет, делителем служит живая база счёта». */
    @Test
    void firstActOfTheDealDividesByTheLiveAccountBase() {
        Deal deal = deal(BigDecimal.ZERO, null);

        // живая база 8000: бюджет 80 < риска акта 92.955
        RiskValidationResult result = validator.validate(
                entryAction("10", ANCHOR, "2910"), context(deal, detail(), appetite(), "8000"));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    /** Пример «снимок базы есть и разошёлся с живой базой — делит снимок, а не счёт». */
    @Test
    void frozenSnapshotDividesInsteadOfTheAccountBase() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));

        // снимок 10000: бюджет 100 > риска акта 92.955, хотя живая база 8000 не пустила бы
        RiskValidationResult result = validator.validate(
                entryAction("10", ANCHOR, "2910"), context(deal, detail(), appetite(), "8000"));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    /** Пустая база риска — делителя не существует, и это отказ, а не ноль. */
    @Test
    void emptyRiskBaseRefusesInsteadOfDividing() {
        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(deal(BigDecimal.ZERO, null), detail(), appetite(), null));

        assertThat(codes(result)).containsExactly(RiskCheckCode.BALANCE_INVALID);
    }

    // --- уровень ПОСЛЕ акта -------------------------------------------------

    /**
     * Пример «постановка основной защиты после адверсного проскока входа:
     * потолок меряется по УСТАНАВЛИВАЕМОМУ уровню, и действие проходит».
     */
    @Test
    void ceilingIsMeasuredByTheLevelTheActEstablishes() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(List.of(), List.of())));
        deal.setPositions(List.of(episode("31", new BigDecimal("3001.125"))));

        // риск живого эпизода по устанавливаемому уровню 2982 = 68.56134375 <= 100
        RiskValidationResult result = validator.validate(protectionAction("2982"), context(deal));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED);
    }

    /**
     * Пример «тот же акт, посчитанный по ДЕЙСТВУЮЩЕМУ (более широкому)
     * уровню: потолок не сходится и постановка блокируется» — контрпример,
     * показывающий, что именно решает операнд уровня.
     */
    @Test
    void theSameActMeasuredByTheStandingLevelWouldBeBlocked() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(List.of(), List.of(protection("2970")))));
        deal.setPositions(List.of(episode("31", new BigDecimal("3001.125"))));

        // акт уровня не касается: считается действующий 2970 = 105.74274375 > 100
        RiskValidationResult result = validator.validate(weakeningAction(), context(deal));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    /**
     * Пример «акт снимает защиту и своей не ставит: уровня после акта нет, и
     * одновременный потолок ОТКАЗЫВАЕТ вычислением, а не считается по
     * снятому».
     */
    @Test
    void noLevelAfterTheActRefusesTheCeilingByComputation() {
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(List.of(), List.of())));
        deal.setPositions(List.of(episode("31", new BigDecimal("3001.125"))));

        RiskValidationResult result = validator.validate(weakeningAction(), context(deal));

        assertThat(codes(result)).contains(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    // --- окно сворачивания --------------------------------------------------

    /** Пример «добор объёма в окне сворачивания отвергается преконтролем». */
    @Test
    void riskCreatingActUnderCollapseIsRejected() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setStatus(Deal.Status.EXIT_PENDING);

        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(deal));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE);
    }

    /** Пример «то же действие вне окна сворачивания преконтролем не отвергается». */
    @Test
    void theSameActOutsideTheCollapseWindowPasses() {
        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(emptyDeal()));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE);
    }

    /**
     * Пример «снятие риска в окне сворачивания проходит: запрет адресует
     * НАБОР риска, а не всякое действие».
     */
    @Test
    void riskRemovingActUnderCollapsePasses() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setStatus(Deal.Status.EXIT_PENDING);

        RiskValidationResult result = validator.validate(weakeningAction(), context(deal));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE);
    }

    // --- блок-сет ступени пары ----------------------------------------------

    /** Пример «перестановка защиты на инструменте под мягкой ступенью отвергается блок-сетом». */
    @Test
    void protectionMoveUnderTheSoftRungIsRejectedByTheBlockSet() {
        givenSafetyRung(Instrument.SafetyRung.ENTRY_BLOCKED);
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));

        RiskValidationResult result = validator.validate(protectionAction("2910"), context(deal));

        assertThat(codes(result)).contains(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    /**
     * Пример «reduce-only-выход на том же инструменте блок-сет не трогает —
     * сделки доживают под защитой»: уровень фиксации прибыли контроля риска
     * не ослабляет и в блок-сет не входит.
     */
    @Test
    void takeProfitPlacementUnderTheSameRungPasses() {
        givenSafetyRung(Instrument.SafetyRung.ENTRY_BLOCKED);
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));

        RiskValidationResult result = validator.validate(takeProfitAction(), context(deal));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    /** Пример «то же действие на инструменте в рабочем состоянии проходит». */
    @Test
    void theSameActOnAWorkingInstrumentPasses() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));

        RiskValidationResult result = validator.validate(protectionAction("2910"), context(deal));

        assertThat(codes(result)).doesNotContain(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    // --- контур инструмента и пары ------------------------------------------

    /** Режим маржи читается со строки ПАРЫ: у проекции каталога колонки под него нет. */
    @Test
    void marginModeIsReadFromThePairRow() {
        AccountInstrumentState state = pairState(Instrument.SafetyRung.ACTIVE);
        state.setMarginMode(Instrument.MarginMode.CROSS);
        when(pairStateDataService.getRequiredByPair(any(), any())).thenReturn(state);

        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(emptyDeal()));

        assertThat(codes(result)).contains(RiskCheckCode.MARGIN_MODE_NOT_ISOLATED);
    }

    /** Плечо выше биржевого максимума отвергается, и читается оно там же — на паре. */
    @Test
    void leverageAboveTheExchangeMaximumIsRejected() {
        AccountInstrumentState state = pairState(Instrument.SafetyRung.ACTIVE);
        state.setLeverage(200);
        when(pairStateDataService.getRequiredByPair(any(), any())).thenReturn(state);

        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2910"), context(emptyDeal()));

        assertThat(codes(result)).contains(RiskCheckCode.EXCHANGE_MAX_LEVERAGE_EXCEEDED);
    }

    /** Вход без резолвимого уровня остановки блокируется, а не сайзится по доле аллокации. */
    @Test
    void riskCreatingEntryWithoutStopIsBlocked() {
        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, null), context(emptyDeal()));

        assertThat(codes(result)).contains(RiskCheckCode.RISK_CREATING_ENTRY_WITHOUT_STOP);
    }

    /**
     * Ставка нужна и действию, уровня не касающемуся, ПОКА ЖИВ ЭПИЗОД: она
     * стои́т операндом живого слагаемого одновременного потолка.
     */
    @Test
    void feeRateIsRequiredWhileTheEpisodeIsLiveEvenWithoutALevelAct() {
        when(rulesDataService.findByInstrumentId(any(), any()))
                .thenReturn(Optional.of(rules("0.1", "1", "1", null)));
        Deal deal = deal(new BigDecimal("100"), new BigDecimal("10000"));
        deal.setTranches(List.of(tranche(List.of(), List.of(protection("2910")))));
        deal.setPositions(List.of(episode("10", ANCHOR)));

        RiskValidationResult result = validator.validate(weakeningAction(), context(deal));

        assertThat(codes(result)).contains(RiskCheckCode.FEE_RATE_UNAVAILABLE);
    }

    /** Пол дистанции стопа: уровень ближе round-trip комиссии отвергается. */
    @Test
    void stopDistanceBelowTheRoundTripFeeFloorIsRejected() {
        RiskValidationResult result = validator.validate(
                entryAction("1", ANCHOR, "2999"), context(emptyDeal()));

        assertThat(codes(result)).contains(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR);
    }

    // --- снятие защиты при живой экспозиции ---------------------------------

    /** Снятие последней защиты над живой экспозицией отвергается покрытием транша. */
    @Test
    void removingTheLastProtectionOverLiveExposureIsRejected() {
        AlgoOrder protection = protection("2910");
        protection.setSize(new BigDecimal("10"));
        DealTranche tranche = tranche(List.of(entryLeg(Order.Status.COMPLETED, "100", "10", "10")),
                List.of(protection));
        tranche.setEntryFilled(new BigDecimal("10"));
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setTranches(List.of(tranche));

        RiskValidationResult result = validator.validateProtectionRemoval(context(deal), tranche, 55L);

        assertThat(codes(result)).contains(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    /** Стоящая ступень блокирует и снятие защиты: оно тоже ослабление контроля риска. */
    @Test
    void standingRungBlocksProtectionRemovalToo() {
        givenSafetyRung(Instrument.SafetyRung.ENTRY_BLOCKED);
        DealTranche tranche = tranche(List.of(), List.of());
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setTranches(List.of(tranche));

        RiskValidationResult result = validator.validateProtectionRemoval(context(deal), tranche, 55L);

        assertThat(codes(result)).contains(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    // --- вторая точка входа: неравенства при нулевом акте --------------------

    /**
     * Живая сделка, перестающая укладываться в потолок при НУЛЕВОМ акте, —
     * находка детектора: акта нет, а неравенство ложно.
     */
    @Test
    void ceilingsWithoutActReportTheBreachOfALiveDeal() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setTranches(new ArrayList<>(List.of(tranche(List.of(), List.of(protection("2000"))))));
        deal.setPositions(new ArrayList<>(List.of(episode("100", ANCHOR))));

        List<RiskCheckResult> breached = validator.ceilingsBreachedWithoutAct(
                context(deal, detail("1", "2", "1", "2"), appetite("1", "2")));

        assertThat(breached.stream().map(RiskCheckResult::getCode))
                .contains(RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED);
    }

    /** Уложившаяся в потолки живая сделка находкой не является. */
    @Test
    void ceilingsWithoutActStaySilentOnAHealthyDeal() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setTranches(new ArrayList<>(List.of(tranche(List.of(), List.of(protection("2990"))))));
        deal.setPositions(new ArrayList<>(List.of(episode("1", ANCHOR))));

        assertThat(validator.ceilingsBreachedWithoutAct(context(deal))).isEmpty();
    }

    /**
     * Неполный граф — МОЛЧАНИЕ, а не находка: операнды потолков на нём
     * занижены, и ложный триггер остановил бы входы по инструменту без
     * основания.
     */
    @Test
    void ceilingsWithoutActStaySilentOnAnIncompleteGraph() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setTranches(new ArrayList<>(List.of(tranche(List.of(), List.of(protection("2000"))))));
        deal.setPositions(new ArrayList<>(List.of(episode("100", ANCHOR))));
        DealContext incomplete = contextBuilder(deal, detail("1", "2", "1", "2"), appetite("1", "2"))
                .graphComplete(false)
                .build();

        assertThat(validator.ceilingsBreachedWithoutAct(incomplete)).isEmpty();
    }

    /**
     * Уровня защиты у живого эпизода не осталось — это ПОТЕРЯ ПОКРЫТИЯ, и
     * её реакцию поднимает свой триггер: одно состояние не получает двух
     * ответов.
     */
    @Test
    void ceilingsWithoutActYieldToTheCoverageTriggerWhenNoStopRemains() {
        Deal deal = deal(BigDecimal.ZERO, new BigDecimal("10000"));
        deal.setTranches(new ArrayList<>(List.of(tranche(List.of(), List.of()))));
        deal.setPositions(new ArrayList<>(List.of(episode("100", ANCHOR))));

        assertThat(validator.ceilingsBreachedWithoutAct(
                context(deal, detail("1", "2", "1", "2"), appetite("1", "2")))).isEmpty();
    }

    // --- фикстуры -----------------------------------------------------------

    private List<RiskCheckCode> codes(RiskValidationResult result) {
        return result.getChecks().stream().map(RiskCheckResult::getCode).toList();
    }

    private void givenSafetyRung(Instrument.SafetyRung rung) {
        when(pairStateDataService.getRequiredByPair(any(), any())).thenReturn(pairState(rung));
    }

    private static AccountInstrumentState pairState(Instrument.SafetyRung rung) {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setExchangeAccountId(ACCOUNT_ID);
        state.setInstrumentId(INSTRUMENT_ID);
        state.setSafetyRung(rung);
        state.setMarginMode(Instrument.MarginMode.ISOLATED);
        return state;
    }

    private DealContext context(Deal deal) {
        return context(deal, detail(), appetite());
    }

    private DealContext context(Deal deal, StrategyDetail detail, Tenant appetite) {
        return context(deal, detail, appetite, "10000");
    }

    private DealContext context(Deal deal, StrategyDetail detail, Tenant appetite, String accountRiskBase) {
        return contextBuilder(deal, detail, appetite, accountRiskBase).build();
    }

    private DealContext.DealContextBuilder contextBuilder(Deal deal, StrategyDetail detail, Tenant appetite) {
        return contextBuilder(deal, detail, appetite, "10000");
    }

    private DealContext.DealContextBuilder contextBuilder(Deal deal, StrategyDetail detail, Tenant appetite,
                                                          String accountRiskBase) {
        when(appetiteDataService.findByTenantInternalId(any())).thenReturn(Optional.ofNullable(appetite));

        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId("ea-0001");
        account.setTenantId(TENANT);
        account.setRiskBase(isBlankBase(accountRiskBase) ? null : new BigDecimal(accountRiskBase));
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("ETH-USDT-SWAP");
        instrument.setExternalSettlementCurrency("USDT");
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .strategyDetail(detail)
                .graphComplete(true);
    }

    private static boolean isBlankBase(String value) {
        return isNull(value);
    }

    private static Deal emptyDeal() {
        return deal(BigDecimal.ZERO, new BigDecimal("10000"));
    }

    private static Deal deal(BigDecimal plannedRisk, BigDecimal frozenBase) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setPlannedRiskAmount(plannedRisk);
        deal.setPlannedRiskEquityBase(frozenBase);
        deal.setTranches(new ArrayList<>());
        deal.setPositions(new ArrayList<>());
        return deal;
    }

    private static DealTranche tranche(List<Order> orders, List<AlgoOrder> protections) {
        DealTranche tranche = new DealTranche();
        tranche.setId(10L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setOrders(new ArrayList<>(orders));
        tranche.setAlgoOrders(new ArrayList<>(protections));
        return tranche;
    }

    private static Order entryLeg(Order.Status status, String plannedRisk, String plannedSize, String filled) {
        Order leg = new Order();
        leg.setId(101L);
        leg.setDealTrancheId(10L);
        leg.setType(Order.Type.ENTRY);
        leg.setStatus(status);
        leg.setPositionReducingOnly(false);
        leg.setPlannedRiskAmount(new BigDecimal(plannedRisk));
        leg.setPlannedSizeContracts(new BigDecimal(plannedSize));
        leg.setAccumulatedFillSize(new BigDecimal(filled));
        leg.setPlannedEntryPrice(ANCHOR);
        leg.setPlannedContractValue(new BigDecimal("0.1"));
        return leg;
    }

    private static AlgoOrder protection(String triggerPrice) {
        TriggerPrice price = new TriggerPrice();
        price.setType(AlgoOrder.TriggerPriceType.MARK);
        price.setValue(new BigDecimal(triggerPrice));
        Trigger trigger = new Trigger();
        trigger.setStopLoss(price);
        Condition condition = new Condition();
        condition.setType(AlgoOrder.ConditionType.STOP_LOSS);
        condition.setTrigger(trigger);
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(55L);
        algoOrder.setDealTrancheId(10L);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setCondition(condition);
        return algoOrder;
    }

    private static Position episode(String size, BigDecimal averagePrice) {
        Position position = new Position();
        position.setId(9L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(new BigDecimal(size));
        position.setExternalAverageEntryPrice(averagePrice);
        return position;
    }

    private static InstrumentExternalRules rules(String contractValue, String lotSize, String minSize,
                                                 String feeRate) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setStatus(InstrumentExternalRules.Status.LIVE);
        rules.setExternalContractValue(contractValue);
        rules.setExternalLotSize(lotSize);
        rules.setExternalMinSize(minSize);
        rules.setExternalTakerFeeRate(feeRate);
        rules.setExternalMaxLeverage("125");
        rules.setExternalTickSize("0.1");
        return rules;
    }

    private static Tenant appetite() {
        return appetite("1", "300");
    }

    private static Tenant appetite(String simultaneousPercent, String catastrophicMultiplier) {
        Tenant tenant = new Tenant();
        tenant.setInternalId(TENANT);
        tenant.setGlobalSimultaneousRiskPerDealPercent(
                isNull(simultaneousPercent) ? null : new BigDecimal(simultaneousPercent));
        tenant.setGlobalCatastrophicRiskPerDealMultiplier(new BigDecimal(catastrophicMultiplier));
        tenant.setGlobalConsecutiveLossLimit(3);
        return tenant;
    }

    private static StrategyDetail detail() {
        return detail("1", "3", "1", "300");
    }

    private static StrategyDetail detail(String perAction, String cumulativeMultiplier,
                                         String strategySimultaneous, String catastrophicMultiplier) {
        StrategyDetail detail = new StrategyDetail();
        detail.setId(21L);
        detail.setRiskPerActionPercent(new BigDecimal(perAction));
        detail.setCumulativeRiskPerDealMultiplier(new BigDecimal(cumulativeMultiplier));
        detail.setStrategySimultaneousRiskPerDealPercent(new BigDecimal(strategySimultaneous));
        detail.setStrategyCatastrophicRiskPerDealMultiplier(
                isNull(catastrophicMultiplier) ? null : new BigDecimal(catastrophicMultiplier));
        return detail;
    }

    private static CalculatedStrategyAction entryAction(String sizeContracts, BigDecimal anchor, String stopPrice) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(1L);
        action.setKey("entry");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setPositionReducingOnly(false);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(anchor, stopPrice))
                .calculatedSize(size(sizeContracts))
                .description("entry")
                .build();
    }

    /** Действие, риска не создающее и уровня не устанавливающее: снятие защиты. */
    private static CalculatedStrategyAction weakeningAction() {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(2L);
        action.setKey("cancel-protection");
        action.setActionType(StrategyActionType.CANCEL_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(null, null))
                .calculatedSize(size("1"))
                .description("weakening")
                .build();
    }

    /** Защитное действие, устанавливающее уровень остановки убытка. */
    private static CalculatedStrategyAction protectionAction(String stopPrice) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(3L);
        action.setKey("protection");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(null, stopPrice))
                .calculatedSize(size("1"))
                .description("protection")
                .build();
    }

    /** Постановка уровня фиксации прибыли: контроля риска не ослабляет. */
    private static CalculatedStrategyAction takeProfitAction() {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(4L);
        action.setKey("take-profit");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.TAKE_PROFIT);
        return CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedPrice(price(null, null))
                .calculatedSize(size("1"))
                .description("take-profit")
                .build();
    }

    private static CalculatedPrice price(BigDecimal anchor, String stopPrice) {
        return CalculatedPrice.builder()
                .purpose(StrategyPricePurpose.ORDER_LIMIT_PRICE)
                .priceMode(PriceMode.EXPLICIT)
                .roundedPrice(anchor)
                .stopLossPrice(isNull(stopPrice) ? null : ResolvedStopLossPrice.builder()
                        .triggerPrice(new BigDecimal(stopPrice))
                        .triggerPriceType(AlgoOrder.TriggerPriceType.MARK)
                        .build())
                .build();
    }

    private static CalculatedSize size(String sizeContracts) {
        return CalculatedSize.builder()
                .sizeContracts(new BigDecimal(sizeContracts))
                .sizeMode(SizeMode.OPEN_OR_INCREASE)
                .build();
    }
}
