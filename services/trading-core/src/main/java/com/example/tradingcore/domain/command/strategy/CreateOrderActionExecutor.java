package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.ResolvedStopLossPrice;
import com.example.strategy.engine.calc.StrategyActionCalculationResult;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.util.RiskMath;
import com.example.tradingcore.domain.calc.CalculationContextFactory;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.AttachedProtectionPayload;
import com.example.tradingcore.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.SubmitOrderCommandPayload;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Планирует создающее действие над обычной заявкой
 * (docs/components/CreateOrderActionExecutor.md).
 *
 * <pre>
 * PLANNED   → расчёт → преконтроль (риск-создающему) → CREATE_ORDER_COMMAND
 * CREATED   → SUBMIT_ORDER_COMMAND
 * SUBMITTED → REFRESH_ORDER_COMMAND
 * </pre>
 *
 * <p><b>На продвинутых стадиях расчёт и преконтроль не повторяются.</b>
 * Нога ведётся по фактам, записанным в цель строки исполнения: пересчёт
 * дал бы новые числа уже поставленной ноге, а плановые шесть — write-once
 * снимок момента постановки (docs/models/domain/core/Order.md).
 *
 * <p><b>Команд не исполняет и статуса сделки не двигает</b> — отдаёт план.
 */
@Component
@RequiredArgsConstructor
public class CreateOrderActionExecutor implements StrategyActionExecutor {

    private final CalculationContextFactory contextFactory;
    private final StrategyActionCalculator calculator;
    private final ActionRiskGate riskGate;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyOrderAction
                && StrategyActionType.CREATE_ACTION.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                           DealContext dealContext, DealTranche tranche) {
        return switch (state.getStatus()) {
            case PLANNED -> planCreation((StrategyOrderAction) action, state, dealContext, tranche);
            case CREATED -> submit(state);
            case SUBMITTED -> refresh(state);
            default -> ActionPlan.nothing();
        };
    }

    /**
     * Первая стадия: посчитать параметры, прогнать преконтроль и собрать
     * команду заведения локальной ноги.
     *
     * <p><b>Преконтроль зовётся только у риск-создающего действия.</b>
     * Reduce-only нога риск снимает; ветвь ослабления защиты к ней не
     * относится, и звать преконтроль на ней значило бы блокировать снятие
     * риска блок-сетом, заведённым против его набора
     * (docs/rules/risk-validator-scope.md).
     */
    private ActionPlan planCreation(StrategyOrderAction action, DealActionState state,
                                    DealContext dealContext, DealTranche tranche) {
        CalculationContext context = contextFactory.build(dealContext, action, tranche);
        StrategyActionCalculationResult result = calculator.calculate(context);
        if (isFalse(result.isSuccess())) {
            return ActionPlan.calculationFailed(result.getError());
        }
        CalculatedStrategyAction calculated = result.getCalculatedAction();
        if (isNotTrue(action.getPositionReducingOnly())) {
            Optional<ActionPlan> blocked = riskGate.gate(calculated, dealContext, tranche);
            if (blocked.isPresent()) {
                return blocked.get();
            }
        }
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CREATE_ORDER_COMMAND)
                .dealId(dealContext.getDeal().getId())
                .dealActionStateId(state.getId())
                .payload(payload(action, calculated, context, tranche))
                .build());
    }

    private ActionPlan submit(DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.SUBMIT_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(new SubmitOrderCommandPayload(state.getTargetEntityId()))
                .build());
    }

    private ActionPlan refresh(DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(new RefreshOrderCommandPayload(state.getTargetEntityId()))
                .build());
    }

    /**
     * Параметры заведения ноги: рассчитанные числа плюс снимок планового
     * риска.
     *
     * <p><b>Шесть чисел планового риска едут вместе или не едут вовсе</b>
     * (docs/models/domain/core/Order.md §«Шесть чисел планового риска»):
     * у reduce-only ноги их нет по построению — она риска не создаёт.
     */
    private CreateOrderCommandPayload payload(StrategyOrderAction action, CalculatedStrategyAction calculated,
                                              CalculationContext context, DealTranche tranche) {
        CalculatedPrice price = calculated.getCalculatedPrice();
        BigDecimal size = calculated.getCalculatedSize().getSizeContracts();
        boolean reducing = isTrue(action.getPositionReducingOnly());
        return CreateOrderCommandPayload.builder()
                .orderType(action.getOrderType())
                .side(action.side())
                .sizeContracts(size)
                .price(price.getRoundedPrice())
                .sendPriceToExchange(price.getSendPriceToExchange())
                .positionReducingOnly(action.getPositionReducingOnly())
                .attachedProtection(attachedProtection(action, price, size))
                .dealTrancheId(isNull(tranche) ? null : tranche.getId())
                .plannedEntryPrice(reducing ? null : price.getRoundedPrice())
                .plannedStopPrice(reducing ? null : stopPrice(price))
                .plannedRiskAmount(reducing ? null : plannedRisk(calculated, context))
                .plannedRiskCurrency(reducing ? null : context.getInstrument().getExternalSettlementCurrency())
                .plannedContractValue(reducing ? null : contractValue(context))
                .liquidationDistanceRatio(liquidationDistanceRatio(context, price))
                .bookDepthAtPlacement(bookDepth(context, action))
                .build();
    }

    /**
     * Плановый риск ноги — убыток на её стопе при постановке.
     *
     * <p>Форма закрыта домом (`docs/spec/risk-at-stop.json`,
     * {@link RiskMath#lossAtStopPerUnit}); экспозицию и размер контракта
     * домножает потребитель — форма их не знает.
     */
    private BigDecimal plannedRisk(CalculatedStrategyAction calculated, CalculationContext context) {
        BigDecimal stop = stopPrice(calculated.getCalculatedPrice());
        BigDecimal anchor = calculated.getCalculatedPrice().getRoundedPrice();
        BigDecimal contractValue = contractValue(context);
        BigDecimal feeRate = feeRate(context);
        if (isNull(stop) || isNull(anchor) || isNull(contractValue) || isNull(feeRate)) {
            return null;
        }
        return RiskMath.lossAtStopPerUnit(context.getStrategyDirection(), anchor, stop, feeRate)
                .multiply(calculated.getCalculatedSize().getSizeContracts())
                .multiply(contractValue);
    }

    private BigDecimal stopPrice(CalculatedPrice price) {
        ResolvedStopLossPrice stop = price.getStopLossPrice();
        return isNull(stop) ? null : stop.getTriggerPrice();
    }

    private BigDecimal contractValue(CalculationContext context) {
        InstrumentExternalRules rules = context.getInstrumentExternalRules();
        return isNull(rules) ? null : rules.contractValue();
    }

    private BigDecimal feeRate(CalculationContext context) {
        InstrumentExternalRules rules = context.getInstrumentExternalRules();
        return isNull(rules) ? null : rules.takerFeeRate();
    }

    /**
     * Запас до ликвидации на момент постановки — <b>измеритель</b>, не
     * операнд: он ничего не блокирует, и пустота у него самостоятельна.
     * Пуст у открывающего входа: цены ликвидации до эпизода не
     * существует (docs/models/domain/core/Order.md).
     */
    private BigDecimal liquidationDistanceRatio(CalculationContext context, CalculatedPrice price) {
        BigDecimal anchor = price.getRoundedPrice();
        BigDecimal liquidation = isNull(context.getActivePosition())
                ? null
                : context.getActivePosition().getExternalLiquidationPrice();
        if (isNull(anchor) || isNull(liquidation) || anchor.signum() == 0) {
            return null;
        }
        return anchor.subtract(liquidation).abs().divide(anchor, DomainMath.CONTEXT);
    }

    /**
     * Ёмкость стакана на момент постановки — измеритель той же природы.
     * Берётся сторона, в которую нога исполняется: покупка съедает ask,
     * продажа — bid. Пуст, когда среза цен в контексте не было.
     */
    private BigDecimal bookDepth(CalculationContext context, StrategyOrderAction action) {
        if (isNull(context.getMarketPriceData())) {
            return null;
        }
        return Objects.equals(Order.Side.BUY, action.side())
                ? context.getMarketPriceData().getExternalAskSize()
                : context.getMarketPriceData().getExternalBidSize();
    }

    /**
     * Встроенная защита внутри заявки: уходит на площадку вместе с ней и
     * потому собирается здесь, а не отдельным действием.
     *
     * <p>Размер защиты равен размеру ноги: встроенная защита прикрывает
     * ровно свою заявку (docs/components/CreateOrderExecutor.md).
     */
    private AttachedProtectionPayload attachedProtection(StrategyOrderAction action, CalculatedPrice price,
                                                         BigDecimal size) {
        StrategyAttachedProtectionSettings settings = action.getAttachedProtection();
        ResolvedStopLossPrice stop = price.getStopLossPrice();
        if (isNull(settings) || isNull(stop)) {
            return null;
        }
        return AttachedProtectionPayload.builder()
                .attachedType(settings.getAttachedType())
                .stopLossTriggerPrice(stop.getTriggerPrice())
                .triggerPriceType(stop.getTriggerPriceType())
                .size(size)
                .build();
    }

}
