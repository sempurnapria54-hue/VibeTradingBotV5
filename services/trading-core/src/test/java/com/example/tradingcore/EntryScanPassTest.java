package com.example.tradingcore;

import static java.util.Objects.nonNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingcore.config.EntryScannerProperties;
import com.example.tradingcore.domain.deal.DealOpeningService;
import com.example.tradingcore.domain.jobs.EntryScannerJob;
import com.example.tradingcore.domain.jobs.JobExecutionGuard;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Отбор входа: гейт двух радиусов, энфорсмент ступеней выборкой, выбор
 * детали фазой и оценка входного условия.
 *
 * <p><b>Что здесь проверяется по существу.</b> Контурная половина гейта
 * не обеспечена ни одним инвариантом базы: пропусти её проход — на счёте
 * заводится вторая сделка, а на ограничении «один инструмент на счёт»
 * стои́т отсрочка уровней риска счёта и портфеля. Ступень пары, не
 * прочитанная отбором, открывает вход по инструменту, торговля которым
 * заморожена. Шаг, оценённый на непокрытых операндах, открывает вход по
 * данным, которым доверять нельзя, — а «устарело» и «нет» неотличимы по
 * построению.
 *
 * <p>Условие входа оценивается НАСТОЯЩИМ интерпретатором грамматики на
 * настоящей раскладке фич — подменённого предиката здесь нет.
 */
class EntryScanPassTest {

    private static final Long ACCOUNT_ID = 2L;
    private static final Long INSTRUMENT_ID = 3L;
    private static final Long SECOND_INSTRUMENT_ID = 4L;
    private static final String EXCHANGE_CODE = "OKX";
    private static final String INDICATOR_KEY = "ema-fast";

    private final ExchangeAccountDataService exchangeAccountDataService =
            mock(ExchangeAccountDataService.class);
    private final AccountInstrumentStateDataService accountInstrumentStateDataService =
            mock(AccountInstrumentStateDataService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final StrategyDataService strategyDataService = mock(StrategyDataService.class);
    private final DealDataService dealDataService = mock(DealDataService.class);
    private final MarketFeatureService marketFeatureService = mock(MarketFeatureService.class);
    private final DealOpeningService dealOpeningService = mock(DealOpeningService.class);
    private final ExchangeOperationsClient exchangeOperationsClient = mock(ExchangeOperationsClient.class);

    /** Интерпретатор НАСТОЯЩИЙ; наблюдатель поверх него нужен там, где предмет проверки — сам факт вызова. */
    private final StrategyConditionEvaluator conditionEvaluator = spy(new StrategyConditionEvaluator());

    // --- гейт входа --------------------------------------------------------

    /**
     * Контурная половина гейта: у счёта уже есть незакрытая сделка — обход
     * его инструментов не начинается вовсе. Без неё ограничение «торгуется
     * один инструмент на счёт» осталось бы операционной дисциплиной.
     */
    @Test
    void anAccountWithAnOpenDealIsNotScanned() {
        stubAccount();
        when(dealDataService.existsActiveOnAccount(ACCOUNT_ID)).thenReturn(Boolean.TRUE);

        job().tick();

        verify(instrumentDataService, never()).findTradable(any(), any());
        verify(dealOpeningService, never()).openDeal(any(), any(), any(), any(), any(), any());
    }

    /**
     * Обход идёт по всем инструментам окна, и вопроса о занятости ПАРЫ
     * сканер не задаёт ни разу.
     *
     * <p><b>Проверяется именно отсутствие вопроса.</b> Обход начинается
     * только после контурной половины гейта — «незакрытой сделки нет ни по
     * одной паре счёта», — а множество сделок пары есть подмножество
     * множества сделок счёта: парный вопрос отвечал бы «свободно» на
     * каждом инструменте окна, то есть был бы чтением с заранее известным
     * ответом. Сам радиус пары при этом энфорсится своими носителями —
     * защитной проверкой в транзакции создания
     * ({@code DealOpeningTest#anOccupiedPairRefusesTheStrategyPath}) и
     * инвариантом базы.
     */
    @Test
    void theScanAsksNoPairQuestionAndWalksTheWholeWindow() {
        stubAccount();
        stubInstruments(instrument(INSTRUMENT_ID), instrument(SECOND_INSTRUMENT_ID));

        job().tick();

        verify(dealDataService, never()).existsActiveOnPair(any(), any());
        verify(strategyDataService).findActiveOnPairWithTree(ACCOUNT_ID, INSTRUMENT_ID);
        verify(strategyDataService).findActiveOnPairWithTree(ACCOUNT_ID, SECOND_INSTRUMENT_ID);
    }

    /**
     * Ступень пары «счёт, инструмент» энфорсится выборкой: инструмент со
     * стоящей ступенью на этом счёте из обхода выпадает, и стратегию по
     * нему никто не читает.
     */
    @Test
    void anInstrumentUnderAStandingRungFallsOutOfTheScan() {
        stubAccount();
        stubInstruments(instrument(INSTRUMENT_ID), instrument(SECOND_INSTRUMENT_ID));
        when(accountInstrumentStateDataService.findInstrumentIdsWithStandingRung(ACCOUNT_ID))
                .thenReturn(List.of(INSTRUMENT_ID));

        job().tick();

        verify(strategyDataService, never()).findActiveOnPairWithTree(ACCOUNT_ID, INSTRUMENT_ID);
        verify(strategyDataService).findActiveOnPairWithTree(ACCOUNT_ID, SECOND_INSTRUMENT_ID);
    }

    /** Пары без активной стратегии отбор пропускает — это штатный ответ. */
    @Test
    void aPairWithoutAnActiveStrategyIsSkipped() {
        stubAccount();
        stubInstruments(instrument(INSTRUMENT_ID));

        job().tick();

        verify(marketFeatureService, never()).readForEntry(any(), any());
    }

    // --- фаза и деталь -----------------------------------------------------

    /**
     * Фаза не резолвится — деталь выбирать не по чему, и отбор молчит, а
     * не берёт деталь наугад.
     */
    @Test
    void anUnresolvedPhaseOpensNothing() {
        Strategy strategy = strategyWith(detail(MarketPhase.Type.BULL_TREND, phaseStep()));
        stubPair(strategy, features(null));

        job().tick();

        verify(dealOpeningService, never()).openDeal(any(), any(), any(), any(), any(), any());
    }

    /** Детали под наблюдённую фазу нет — входить не по чему. */
    @Test
    void aPhaseWithoutItsDetailOpensNothing() {
        Strategy strategy = strategyWith(detail(MarketPhase.Type.RANGE, phaseStep()));
        stubPair(strategy, features(MarketPhase.Type.BULL_TREND));

        job().tick();

        verify(dealOpeningService, never()).openDeal(any(), any(), any(), any(), any(), any());
    }

    /** Политика входа фазы запрещает торговлю — стоп до оценки условий. */
    @Test
    void aPhaseForbiddenByThePolicyOpensNothing() {
        StrategyDetail detail = detail(MarketPhase.Type.BULL_TREND, phaseStep());
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.NO_TRADE);
        stubPair(strategyWith(detail), features(MarketPhase.Type.BULL_TREND));

        job().tick();

        verify(dealOpeningService, never()).openDeal(any(), any(), any(), any(), any(), any());
    }

    // --- свежесть и условие ------------------------------------------------

    /**
     * Гейт свежести: операнды шага не покрыты снятой раскладкой — шаг до
     * оценки условия не доходит и сделка не создаётся. Устаревшее и
     * отсутствующее ключа не занимают, и различать их читателю не нужно.
     */
    @Test
    void aStepWhoseOperandsAreNotCoveredDoesNotOpenADeal() {
        StrategyDetail detail = detail(MarketPhase.Type.BULL_TREND, indicatorStep());
        stubPair(strategyWith(detail), features(MarketPhase.Type.BULL_TREND));

        job().tick();

        verify(conditionEvaluator, never()).evaluate(any(), any());
        verify(dealOpeningService, never()).openDeal(any(), any(), any(), any(), any(), any());
    }

    /**
     * Условие выполнено — сделка заводится: направление читается с
     * действия шага, фаза передаётся той, по которой выбрана деталь, а
     * момент создания — биржевой.
     */
    @Test
    void aSatisfiedEntryConditionOpensTheDeal() {
        StrategyDetail detail = detail(MarketPhase.Type.BULL_TREND, phaseStep());
        stubPair(strategyWith(detail), features(MarketPhase.Type.BULL_TREND));
        OffsetDateTime serverTime = OffsetDateTime.of(2026, 9, 6, 10, 0, 0, 0, ZoneOffset.UTC);
        when(exchangeOperationsClient.getServerTime()).thenReturn(serverTime);
        when(dealOpeningService.openDeal(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new Deal()));

        job().tick();

        verify(dealOpeningService).openDeal(any(), any(), eq(detail),
                eq(StrategyTradeDirection.LONG), eq(MarketPhase.Type.BULL_TREND), eq(serverTime));
    }

    /** Условие не выполнено — сделки нет, и биржевой момент не запрашивается. */
    @Test
    void anUnsatisfiedEntryConditionOpensNothing() {
        StrategyDetail detail = detail(MarketPhase.Type.RANGE, phaseStep());
        stubPair(strategyWith(detail), features(MarketPhase.Type.RANGE));

        job().tick();

        verify(dealOpeningService, never()).openDeal(any(), any(), any(), any(), any(), any());
        verify(exchangeOperationsClient, never()).getServerTime();
    }

    /**
     * Заведённая сделка закрывает счёт контурной половиной гейта, и обход
     * его инструментов прекращается тем же тиком.
     */
    @Test
    void anOpenedDealStopsTheScanOfItsAccount() {
        StrategyDetail detail = detail(MarketPhase.Type.BULL_TREND, phaseStep());
        Strategy strategy = strategyWith(detail);
        Instrument first = instrument(INSTRUMENT_ID);
        stubAccount();
        stubInstruments(first, instrument(SECOND_INSTRUMENT_ID));
        when(strategyDataService.findActiveOnPairWithTree(eq(ACCOUNT_ID), any()))
                .thenReturn(Optional.of(strategy));
        when(marketFeatureService.readForEntry(eq(strategy), any()))
                .thenReturn(features(MarketPhase.Type.BULL_TREND));
        when(dealOpeningService.openDeal(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new Deal()));

        job().tick();

        verify(dealOpeningService).openDeal(any(), eq(first), any(), any(), any(), any());
        verify(strategyDataService, never()).findActiveOnPairWithTree(ACCOUNT_ID, SECOND_INSTRUMENT_ID);
    }

    /** Выключенный отбор не делает ничего — ни выборки счетов, ни обхода. */
    @Test
    void aDisabledScanDoesNothing() {
        EntryScannerProperties disabled = new EntryScannerProperties();
        disabled.setEnabled(Boolean.FALSE);

        job(disabled).tick();

        verify(exchangeAccountDataService, never()).findEntryEligibleAccounts();
    }

    // --- сборка ------------------------------------------------------------

    private EntryScannerJob job() {
        return job(new EntryScannerProperties());
    }

    private EntryScannerJob job(EntryScannerProperties properties) {
        return new EntryScannerJob(properties, new JobExecutionGuard(), exchangeAccountDataService,
                accountInstrumentStateDataService, instrumentDataService, strategyDataService,
                dealDataService, marketFeatureService, conditionEvaluator, dealOpeningService,
                exchangeOperationsClient);
    }

    /** Один счёт, доступный для входа, без стоящих ступеней на парах. */
    private void stubAccount() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setExchangeCode(EXCHANGE_CODE);
        when(exchangeAccountDataService.findEntryEligibleAccounts())
                .thenReturn(new ArrayList<>(List.of(account)));
        when(accountInstrumentStateDataService.findInstrumentIdsWithStandingRung(ACCOUNT_ID))
                .thenReturn(List.of());
    }

    private void stubInstruments(Instrument... instruments) {
        when(instrumentDataService.findTradable(eq(EXCHANGE_CODE), any()))
                .thenReturn(new ArrayList<>(List.of(instruments)));
    }

    /** Счёт с одним инструментом, активной стратегией и снятой раскладкой фич. */
    private void stubPair(Strategy strategy, MarketFeatures features) {
        stubAccount();
        stubInstruments(instrument(INSTRUMENT_ID));
        when(strategyDataService.findActiveOnPairWithTree(ACCOUNT_ID, INSTRUMENT_ID))
                .thenReturn(Optional.of(strategy));
        when(marketFeatureService.readForEntry(eq(strategy), any())).thenReturn(features);
    }

    private Instrument instrument(Long id) {
        Instrument instrument = new Instrument();
        instrument.setId(id);
        instrument.setInternalId("instrument-" + id);
        instrument.setExchangeCode(EXCHANGE_CODE);
        return instrument;
    }

    /**
     * Раскладка фич момента: фаза есть либо её нет, индикаторных
     * операндов не снято ни одного — на них и стои́т проверка гейта
     * свежести.
     */
    private MarketFeatures features(MarketPhase.Type phaseType) {
        MarketPhase phase = null;
        if (nonNull(phaseType)) {
            phase = new MarketPhase();
            phase.setType(phaseType);
        }
        return MarketFeatures.builder()
                .latestIndicators(Map.of())
                .previousIndicators(Map.of())
                .structures(Map.of())
                .marketPhase(phase)
                .build();
    }

    private Strategy strategyWith(StrategyDetail detail) {
        Strategy strategy = new Strategy();
        strategy.setId(11L);
        strategy.setInternalId("strategy-11");
        strategy.setExchangeAccountInternalId("ea-0002");
        strategy.setInstrumentInternalId("in-0003");
        strategy.setStatus(Strategy.Status.ACTIVE);
        strategy.setDetails(new ArrayList<>(List.of(detail)));
        return strategy;
    }

    private StrategyDetail detail(MarketPhase.Type phaseType, StrategyStep entryStep) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(31L);
        declaration.setKey("entry");
        Map<DealTranche.Status, List<StrategyStep>> steps = new LinkedHashMap<>();
        steps.put(DealTranche.Status.PRECHECK, new ArrayList<>(List.of(entryStep)));
        declaration.setStepsByStatus(steps);
        StrategyDetail detail = new StrategyDetail();
        detail.setId(21L);
        detail.setMarketPhaseType(phaseType);
        detail.setPhaseEntryPolicy(policyFor(phaseType));
        detail.setTranches(new ArrayList<>(List.of(declaration)));
        return detail;
    }

    /** Политика, допускающая вход в этой фазе, — иначе деталь не торгуется. */
    private PhaseEntryPolicy policyFor(MarketPhase.Type phaseType) {
        return MarketPhase.Type.RANGE.equals(phaseType) ? PhaseEntryPolicy.GRID : PhaseEntryPolicy.FOLLOW_PHASE;
    }

    /** Входной шаг с условием фазы: истинен ровно в бычьем тренде. */
    private StrategyStep phaseStep() {
        StrategyConditionOperand declared = new StrategyConditionOperand();
        declared.setSourceType(StrategyConditionSourceType.CONSTANT);
        declared.setValueType(ConstantValueType.ENUM);
        declared.setValue(MarketPhase.Type.BULL_TREND.name());
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.MARKET_PHASE_IS);
        rule.setRightOperand(declared);
        return entryStep(new StrategyCondition(new ArrayList<>(List.of(rule))));
    }

    /** Входной шаг с индикаторным условием — его операнда в раскладке нет. */
    private StrategyStep indicatorStep() {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.INDICATOR);
        left.setIndicatorKey(INDICATOR_KEY);
        StrategyConditionOperand right = new StrategyConditionOperand();
        right.setSourceType(StrategyConditionSourceType.CONSTANT);
        right.setValueType(ConstantValueType.NUMBER);
        right.setValue("0");
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return entryStep(new StrategyCondition(new ArrayList<>(List.of(rule))));
    }

    private StrategyStep entryStep(StrategyCondition condition) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(41L);
        action.setKey("entry-order");
        action.setDirection(StrategyTradeDirection.LONG);
        StrategyStep step = new StrategyStep();
        step.setId(51L);
        step.setStepType(StrategyStepType.ENTRY);
        step.setCondition(condition);
        step.setActions(new ArrayList<>(List.of(action)));
        return step;
    }
}
