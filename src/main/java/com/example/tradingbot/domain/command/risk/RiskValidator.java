package com.example.tradingbot.domain.command.risk;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.calc.CalculatedPrice;
import com.example.tradingbot.domain.command.calc.CalculatedSize;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.PriceMode;
import com.example.tradingbot.domain.command.calc.ResolvedStopLossPrice;
import com.example.tradingbot.domain.command.calc.ResolvedTakeProfitPrice;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingbot.domain.command.risk.RiskValidationResult.RiskDecision;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Проверяет уже рассчитанное risk-creating/increasing/weakening действие
 * по risk-policy и отвечает «разрешено ли действие?», возвращая
 * {@link RiskValidationResult} (docs/components/RiskValidator.md). Сам
 * считает нужные risk-метрики (risk amount, risk percent от свободного
 * депозита). Читает persisted InstrumentExternalRules — в биржу не ходит;
 * статус сделки не меняет и команд не создаёт. Фаза 1 — риск на сделку:
 * главная проверка RISK_PER_TRADE_EXCEEDED (база —
 * externalAvailableEquity); кода контроля экспозиции/нашего кэпа плеча нет
 * (docs/decisions/per-trade-risk-policy.md).
 */
@Component
@RequiredArgsConstructor
public class RiskValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InstrumentExternalRulesDataService rulesDataService;

    public RiskValidationResult validate(CalculatedStrategyAction calculatedAction, DealContext dealContext) {
        List<RiskCheckResult> checks = new ArrayList<>();
        CalculatedSize size = calculatedAction.getCalculatedSize();
        CalculatedPrice price = calculatedAction.getCalculatedPrice();

        if (isNull(size) || isNull(size.getSizeContracts()) || size.getSizeContracts().signum() <= 0) {
            return blockedResult(checks, RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Calculated size missing or non-positive");
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(dealContext.getInstrument().getId())
                .orElse(null);
        if (isNull(rules)) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_RULES_MISSING,
                    "Instrument external rules not materialized");
        }
        BalanceContainer balance = dealContext.getBalanceContainer();
        if (isNull(balance) || isNull(balance.getExternalAvailableEquity())
                || balance.getExternalAvailableEquity().signum() <= 0) {
            return blockedResult(checks, RiskCheckCode.BALANCE_INVALID,
                    "Available equity missing or non-positive");
        }

        BigDecimal sizeContracts = size.getSizeContracts();
        BigDecimal equity = balance.getExternalAvailableEquity();
        Position position = dealContext.getDeal().livePosition();
        StrategyTradeDirection direction = dealContext.getDeal().getDirection();
        BigDecimal entryReference = entryReference(position, price);

        checkInstrumentLive(rules, checks);
        checkMarginMode(dealContext.getInstrument(), checks);
        checkSizeBounds(rules, sizeContracts, price, checks);
        checkExchangeMaxLeverage(dealContext.getInstrument(), rules, checks);
        checkRiskCreatingEntryProtection(calculatedAction, checks);
        checkStopLossSide(price.getStopLossPrice(), entryReference, direction, checks);
        checkTakeProfitSide(price.getTakeProfitPrice(), entryReference, direction, checks);
        checkLiquidationGuard(price.getStopLossPrice(), position, direction, checks);
        checkRiskPerTrade(price.getStopLossPrice(), entryReference, sizeContracts, rules, equity,
                dealContext.getStrategyDetail(), checks);

        return aggregate(checks);
    }

    private void checkInstrumentLive(InstrumentExternalRules rules, List<RiskCheckResult> checks) {
        if (isFalse(rules.isLive())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_NOT_LIVE,
                    "Instrument not tradeable: " + rules.getStatus(), null));
        }
    }

    private void checkMarginMode(Instrument instrument, List<RiskCheckResult> checks) {
        if (isFalse(Instrument.MarginMode.ISOLATED.equals(instrument.getMarginMode()))) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.MARGIN_MODE_NOT_ISOLATED,
                    "Margin mode not isolated: " + instrument.getMarginMode(), null));
        }
    }

    private void checkSizeBounds(InstrumentExternalRules rules, BigDecimal sizeContracts, CalculatedPrice price,
                                 List<RiskCheckResult> checks) {
        BigDecimal minSize = rules.minSize();
        if (nonNull(minSize) && sizeContracts.compareTo(minSize) < 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.SIZE_BELOW_MIN,
                    "Size below instrument minimum " + minSize, sizeContracts));
        }
        BigDecimal lotSize = rules.lotSize();
        if (nonNull(lotSize) && lotSize.signum() > 0 && sizeContracts.remainder(lotSize).signum() != 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.SIZE_LOT_STEP_INVALID,
                    "Size not a multiple of lot step " + lotSize, sizeContracts));
        }
        BigDecimal maxSize = applicableMaxSize(rules, price);
        if (nonNull(maxSize) && sizeContracts.compareTo(maxSize) > 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.SIZE_ABOVE_LIMIT,
                    "Size above per-order limit " + maxSize, sizeContracts));
        }
    }

    private void checkExchangeMaxLeverage(Instrument instrument, InstrumentExternalRules rules,
                                          List<RiskCheckResult> checks) {
        BigDecimal maxLeverage = rules.maxLeverage();
        if (isNull(instrument.getLeverage()) || isNull(maxLeverage)) {
            return;
        }
        BigDecimal leverage = new BigDecimal(instrument.getLeverage());
        if (leverage.compareTo(maxLeverage) > 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.EXCHANGE_MAX_LEVERAGE_EXCEEDED,
                    "Leverage above exchange max " + maxLeverage, leverage));
        }
    }

    /**
     * Инвариант docs/rules/risk-creating-entry-protection.md: risk-creating вход
     * (открытие/наращивание позиции, не reduce-only) без резолвимого стопа
     * блокируется — без fail-open allocation-сайзинга в обход RISK_PER_TRADE.
     * Резолвимый стоп = attached SL / иной механизм, давший цену стопа в
     * CalculatedPrice. Reduce-only/закрывающие действия правило не трогает.
     */
    private void checkRiskCreatingEntryProtection(CalculatedStrategyAction calculatedAction,
                                                  List<RiskCheckResult> checks) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))) {
            return;
        }
        ResolvedStopLossPrice stopLoss = calculatedAction.getCalculatedPrice().getStopLossPrice();
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_CREATING_ENTRY_WITHOUT_STOP,
                    "Risk-creating entry without resolvable stop-loss", null));
        }
    }

    /** Risk-creating вход — order-action, открывающий/наращивающий позицию (не reduce-only). */
    private Boolean isRiskCreatingEntry(StrategyAction action) {
        if (action instanceof StrategyOrderAction orderAction) {
            return isNotTrue(orderAction.getPositionReducingOnly());
        }
        return false;
    }

    private void checkStopLossSide(ResolvedStopLossPrice stopLoss, BigDecimal entryReference,
                                   StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice()) || isNull(entryReference)) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        boolean invalid = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(entryReference) >= 0
                : trigger.compareTo(entryReference) <= 0;
        if (invalid) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                    "Stop-loss on wrong side of entry " + entryReference, trigger));
        }
    }

    private void checkTakeProfitSide(ResolvedTakeProfitPrice takeProfit, BigDecimal entryReference,
                                     StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(takeProfit) || isNull(takeProfit.getTriggerPrice()) || isNull(entryReference)) {
            return;
        }
        BigDecimal trigger = takeProfit.getTriggerPrice();
        boolean invalid = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(entryReference) <= 0
                : trigger.compareTo(entryReference) >= 0;
        if (invalid) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.TAKE_PROFIT_INVALID_SIDE,
                    "Take-profit on wrong side of entry " + entryReference, trigger));
        }
    }

    private void checkLiquidationGuard(ResolvedStopLossPrice stopLoss, Position position,
                                       StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice())
                || isNull(position) || isNull(position.getExternalLiquidationPrice())) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        BigDecimal liquidation = position.getExternalLiquidationPrice();
        boolean tooClose = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(liquidation) <= 0
                : trigger.compareTo(liquidation) >= 0;
        if (tooClose) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,
                    "Stop-loss beyond liquidation price " + liquidation, trigger));
        }
    }

    private void checkRiskPerTrade(ResolvedStopLossPrice stopLoss, BigDecimal entryReference, BigDecimal sizeContracts,
                                   InstrumentExternalRules rules, BigDecimal equity, StrategyDetail strategyDetail,
                                   List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice()) || isNull(entryReference)
                || isNull(strategyDetail) || isNull(strategyDetail.getRiskPerTradePercent())
                || isNull(rules.contractValue())) {
            return;
        }
        BigDecimal stopDistance = entryReference.subtract(stopLoss.getTriggerPrice()).abs();
        BigDecimal riskAmount = stopDistance.multiply(sizeContracts).multiply(rules.contractValue());
        BigDecimal riskPercent = riskAmount.divide(equity, Constants.Calc.MATH_CONTEXT).multiply(HUNDRED);
        if (riskPercent.compareTo(strategyDetail.getRiskPerTradePercent()) > 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_PER_TRADE_EXCEEDED,
                    "Per-trade risk exceeds limit " + strategyDetail.getRiskPerTradePercent() + "%", riskPercent));
        }
    }

    /** Цена-ориентир входа: средняя цена открытой позиции либо рассчитанная цена входа. */
    private BigDecimal entryReference(Position position, CalculatedPrice price) {
        if (nonNull(position) && nonNull(position.getExternalAverageEntryPrice())) {
            return position.getExternalAverageEntryPrice();
        }
        return isNull(price) ? null : price.getRoundedPrice();
    }

    /** Per-order лимит размера по режиму цены: EXPLICIT → limit-лимит, иначе → market-лимит. */
    private BigDecimal applicableMaxSize(InstrumentExternalRules rules, CalculatedPrice price) {
        if (nonNull(price) && PriceMode.EXPLICIT.equals(price.getPriceMode())) {
            return rules.maxLimitSize();
        }
        return rules.maxMarketSize();
    }

    private RiskValidationResult blockedResult(List<RiskCheckResult> checks, RiskCheckCode code, String comment) {
        checks.add(RiskCheckResult.blocked(code, comment, null));
        return RiskValidationResult.builder()
                .decision(RiskDecision.BLOCKED)
                .checks(checks)
                .comment(comment)
                .build();
    }

    private RiskValidationResult aggregate(List<RiskCheckResult> checks) {
        RiskDecision decision = RiskDecision.ALLOWED;
        if (checks.stream().anyMatch(check -> RiskCheckStatus.BLOCKED.equals(check.getStatus()))) {
            decision = RiskDecision.BLOCKED;
        } else if (checks.stream().anyMatch(check -> RiskCheckStatus.WARNING.equals(check.getStatus()))) {
            decision = RiskDecision.WARNING;
        }
        return RiskValidationResult.builder()
                .decision(decision)
                .checks(checks)
                .comment("risk validation " + decision)
                .build();
    }
}
