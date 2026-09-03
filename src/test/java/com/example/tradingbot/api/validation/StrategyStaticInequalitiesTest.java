package com.example.tradingbot.api.validation;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.api.model.request.CreateStrategyApiRequest;
import com.example.tradingbot.api.model.strategy.StrategyActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyDetailApiModel;
import com.example.tradingbot.api.model.strategy.StrategyOrderActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyStepApiModel;
import com.example.tradingbot.api.model.strategy.StrategyTrancheApiModel;
import com.example.tradingbot.config.RiskAppetiteProperties;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Три статических неравенства создания, у которых прежде не было
 * энфорсера: веер риска, запас нотинала под катастрофическим потолком и
 * полнота покрытия защитой
 * (docs/rules/strategy-validation.md §«Исключения: неравенства,
 * проверяемые на создании»; исполнимые формы —
 * docs/spec/strategy-reference.json, величины
 * {@code overlapRiskSatisfiable}, {@code notionalHeadroomSatisfied},
 * {@code protectionCoverageComplete}, {@code partialStepsReturningLess}).
 *
 * <p>Несущее: неравенство объявлено домом и спекой, реджект-код назван —
 * а в коде проверки не было ни одной, и конфигурация, не работающая
 * НИ ПРИ КАКОМ рынке, создавалась без отказа. Каждый тест — падающая
 * проба своей оси плюс контроль на границе: без границы проверка,
 * отвергающая всё подряд, выглядела бы работающей.
 */
class StrategyStaticInequalitiesTest {

    @Test
    @DisplayName("Веер выше максимума одновременного риска — свой реджект")
    void overlapAboveSimultaneousMaximumIsRejected() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(2, 1));

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE"), reason);
        assertFalse(reason.contains("STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL"),
                "адресует отказ тот конъюнкт, который ложен");
    }

    @Test
    @DisplayName("Контроль веера: N_overlap = 1 при равенстве потолку проходит")
    void singleTrancheFanPassesAtBoundary() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));

        assertFalse(reasonOrEmpty(request).contains("STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE"));
    }

    @Test
    @DisplayName("Объявленный нотинал без запаса под потолком — создание отвергается")
    void notionalWithoutHeadroomIsRejected() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));
        entryAction(request).setAllocationPercents(new BigDecimal("100"));

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT"), reason);
    }

    @Test
    @DisplayName("Контроль запаса: ровно на границе запаса нотинал проходит")
    void notionalAtHeadroomBoundaryPasses() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));
        entryAction(request).setAllocationPercents(new BigDecimal("99"));

        assertFalse(reasonOrEmpty(request).contains("STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT"));
    }

    @Test
    @DisplayName("Шаг полного набора защиты объявляет меньше ста процентов — отказ")
    void fullSetStepBelowHundredIsRejected() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));
        protectionAction(request).setCloseFractionPercents(new BigDecimal("50"));

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_PROTECTION_COVERAGE_INCOMPLETE"), reason);
    }

    @Test
    @DisplayName("Шаг полного набора выше ста процентов — тот же отказ: закрыть больше, чем есть, нельзя")
    void fullSetStepAboveHundredIsRejected() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));
        protectionAction(request).setCloseFractionPercents(new BigDecimal("150"));

        assertTrue(rejectReason(request).contains("STRATEGY_PROTECTION_COVERAGE_INCOMPLETE"));
    }

    @Test
    @DisplayName("Частичный шаг возвращает меньше забранного — тот же класс, тот же реджект")
    void partialStepReturningLessIsRejected() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));
        StrategyTrancheApiModel tranche = request.getDetails().getFirst().getTranches().getFirst();
        tranche.getStepsByStatus().put(DealTranche.Status.MANAGING.name(),
                List.of(step(StrategyStepType.PROTECTION_ADJUSTMENT,
                        List.of(replaceProtection("partial_replace", "protection_partial",
                                new BigDecimal("10"))))));

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_PROTECTION_COVERAGE_INCOMPLETE"), reason);
    }

    @Test
    @DisplayName("Контроль частичного шага: возврат не меньше забранного проходит")
    void partialStepReturningSamePasses() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));
        StrategyTrancheApiModel tranche = request.getDetails().getFirst().getTranches().getFirst();
        tranche.getStepsByStatus().put(DealTranche.Status.MANAGING.name(),
                List.of(step(StrategyStepType.PROTECTION_ADJUSTMENT,
                        List.of(replaceProtection("partial_replace", "protection_partial",
                                new BigDecimal("25"))))));

        assertFalse(reasonOrEmpty(request).contains("STRATEGY_PROTECTION_COVERAGE_INCOMPLETE"));
    }

    @Test
    @DisplayName("Минимальная законная деталь проходит все три неравенства")
    void minimalTradableDetailPasses() {
        CreateStrategyApiRequest request = requestWithTranches(levelCounts(1));
        // Шаг MAIN_PROTECTION объявляет полный набор и даёт ровно сто процентов (75 + 25);
        // веер = 1; объявленный нотинал = 0.95 базы при допустимых 0.99.
        assertDoesNotThrow(() -> validator().validateCreate(request));
    }

    private StrategyOrderActionApiModel entryAction(CreateStrategyApiRequest request) {
        return (StrategyOrderActionApiModel) request.getDetails().getFirst().getTranches().getFirst()
                .getStepsByStatus().get(DealTranche.Status.PRECHECK.name()).getFirst().getActions().getFirst();
    }

    private StrategyAlgoOrderActionApiModel protectionAction(CreateStrategyApiRequest request) {
        return (StrategyAlgoOrderActionApiModel) request.getDetails().getFirst().getTranches().getFirst()
                .getStepsByStatus().get(DealTranche.Status.ENTRY_FINALIZED.name()).getFirst()
                .getActions().getFirst();
    }

    private String rejectReason(CreateStrategyApiRequest request) {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> validator().validateCreate(request));
        return failure.getReason();
    }

    /** Отказ, если он был; иначе пусто — для контролей на границе. */
    private String reasonOrEmpty(CreateStrategyApiRequest request) {
        try {
            validator().validateCreate(request);
            return "";
        } catch (ResponseStatusException e) {
            return e.getReason();
        }
    }

    private StrategyCreateRequestValidator validator() {
        RiskAppetiteProperties appetite = new RiskAppetiteProperties();
        appetite.setGlobalSimultaneousRiskPerDealPercent(BigDecimal.ONE);
        appetite.setGlobalCatastrophicRiskPerDealMultiplier(new BigDecimal("100"));
        appetite.setGlobalConsecutiveLossLimit(3);
        return new StrategyCreateRequestValidator(appetite);
    }

    private List<Integer> levelCounts(Integer... counts) {
        return List.of(counts);
    }

    /**
     * Торгуемая деталь с объявлениями заданной мощности: у первого —
     * вход и шаг полного набора защиты, у прочих — только мощность.
     */
    private CreateStrategyApiRequest requestWithTranches(List<Integer> levelCounts) {
        StrategyDetailApiModel detail = new StrategyDetailApiModel();
        detail.setMarketPhaseType(MarketPhase.Type.BULL_TREND.name());
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.FOLLOW_PHASE.name());
        detail.setRiskPerActionPercent(BigDecimal.ONE);
        detail.setCumulativeRiskPerDealMultiplier(new BigDecimal("2"));
        detail.setStrategySimultaneousRiskPerDealPercent(BigDecimal.ONE);
        detail.setStrategyCatastrophicRiskPerDealMultiplier(new BigDecimal("100"));

        detail.setTranches(java.util.stream.IntStream.range(0, levelCounts.size())
                .mapToObj(index -> tranche("t" + index, levelCounts.get(index), index == 0))
                .collect(java.util.stream.Collectors.toList()));

        List<StrategyDetailApiModel> details = new java.util.ArrayList<>(List.of(detail));
        // Прочие фазы объявляются явно неторгуемыми: покрытие матрицы фаз —
        // отдельная охрана, и без него отказ нёс бы чужие нарушения.
        for (MarketPhase.Type phase : MarketPhase.Type.values()) {
            if (isFalse(MarketPhase.Type.BULL_TREND.equals(phase))) {
                details.add(nonTradingDetail(phase));
            }
        }

        CreateStrategyApiRequest request = new CreateStrategyApiRequest();
        request.setDetails(details);
        return request;
    }

    private StrategyDetailApiModel nonTradingDetail(MarketPhase.Type phase) {
        StrategyDetailApiModel detail = new StrategyDetailApiModel();
        detail.setMarketPhaseType(phase.name());
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.NO_TRADE.name());
        return detail;
    }

    private StrategyTrancheApiModel tranche(String key, Integer levelCount, boolean withEntry) {
        StrategyTrancheApiModel tranche = new StrategyTrancheApiModel();
        tranche.setKey(key);
        tranche.setLevelCount(levelCount);
        tranche.setPositionReopenAllowed(Boolean.FALSE);
        if (levelCount > 1) {
            tranche.setLevelStep(new BigDecimal("0.5"));
        }
        if (withEntry) {
            tranche.setStepsByStatus(new java.util.LinkedHashMap<>(Map.of(
                    DealTranche.Status.PRECHECK.name(),
                    List.of(step(StrategyStepType.ENTRY, List.of(entry()))),
                    DealTranche.Status.ENTRY_FINALIZED.name(),
                    List.of(step(StrategyStepType.MAIN_PROTECTION,
                            List.of(protection(), partialProtection()))))));
        }
        return tranche;
    }

    private StrategyStepApiModel step(StrategyStepType type, List<StrategyActionApiModel> actions) {
        StrategyStepApiModel step = new StrategyStepApiModel();
        step.setStepType(type.name());
        step.setActions(actions);
        return step;
    }

    private StrategyActionApiModel entry() {
        StrategyOrderActionApiModel action = new StrategyOrderActionApiModel();
        action.setKey("entry");
        action.setActionType(StrategyActionType.CREATE_ACTION.name());
        action.setOrderType(Order.Type.ENTRY.name());
        action.setDirection(StrategyTradeDirection.LONG.name());
        action.setAllocationPercents(new BigDecimal("95"));
        return action;
    }

    private StrategyActionApiModel protection() {
        StrategyAlgoOrderActionApiModel action = new StrategyAlgoOrderActionApiModel();
        action.setKey("protection");
        action.setActionType(StrategyActionType.CREATE_ACTION.name());
        action.setConditionType(AlgoOrder.ConditionType.OCO_FULL.name());
        action.setCloseFractionPercents(new BigDecimal("75"));
        return action;
    }

    /**
     * Вторая защита того же шага: она и есть ЦЕЛЬ частичного замещения.
     * Сумма шага полного набора — сто процентов, поэтому сам он законен.
     */
    private StrategyActionApiModel partialProtection() {
        StrategyAlgoOrderActionApiModel action = new StrategyAlgoOrderActionApiModel();
        action.setKey("protection_partial");
        action.setActionType(StrategyActionType.CREATE_ACTION.name());
        action.setConditionType(AlgoOrder.ConditionType.PARTIAL_STOP_LOSS.name());
        action.setCloseFractionPercents(new BigDecimal("25"));
        return action;
    }

    private StrategyActionApiModel replaceProtection(String key, String targetKey, BigDecimal fraction) {
        StrategyAlgoOrderActionApiModel action = new StrategyAlgoOrderActionApiModel();
        action.setKey(key);
        action.setTargetActionKey(targetKey);
        action.setActionType(StrategyActionType.REPLACE_ACTION.name());
        action.setConditionType(AlgoOrder.ConditionType.OCO_FULL.name());
        action.setCloseFractionPercents(fraction);
        return action;
    }
}
