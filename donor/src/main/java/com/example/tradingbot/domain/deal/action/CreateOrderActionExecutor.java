package com.example.tradingbot.domain.deal.action;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculationResult;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.command.risk.DealRiskNumbers;
import com.example.tradingbot.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per-pass executor CREATE-действия над ordinary order. По стадии
 * {@link DealActionState}: PLANNED → расчёт → risk (для risk-creating, т.е.
 * не reduce-only) → CREATE_ORDER; CREATED → SUBMIT_ORDER; SUBMITTED →
 * REFRESH_ORDER. На продвинутых стадиях расчёт/риск не повторяются (нога по
 * фактам из target). Risk-block отдаётся {@link ActionPlan}'ом (реакцию
 * решает resolver в handler'е). См. docs/processes/fsm-execution-layering.md.
 */
@Component
@RequiredArgsConstructor
public class CreateOrderActionExecutor implements StrategyActionExecutor {

    private final StrategyActionCalculator calculator;
    private final ActionRiskGate riskGate;
    private final InstrumentExternalRulesDataService rulesDataService;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyOrderAction
                && StrategyActionType.CREATE_ACTION.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext,
                           DealTranche tranche) {
        return switch (state.getStatus()) {
            case PLANNED -> initialCommand((StrategyOrderAction) action, state, dealContext, tranche);
            case CREATED -> submitCommand(state, dealContext);
            case SUBMITTED -> refreshCommand(state, dealContext);
            default -> ActionPlan.empty();
        };
    }

    private ActionPlan initialCommand(StrategyOrderAction action, DealActionState state, DealContext dealContext,
                                      DealTranche tranche) {
        StrategyActionCalculationResult calculation = calculator.calculate(action, dealContext);
        if (StrategyActionCalculationResult.Status.ERROR.equals(calculation.getStatus())) {
            return ActionPlan.calcError(calculation.getError());
        }
        CalculatedStrategyAction calculated = calculation.getCalculatedAction();
        if (isRiskCreating(action)) {
            ActionPlan blocked = riskGate.blockingPlan(calculated, dealContext, tranche);
            if (nonNull(blocked)) {
                return blocked;
            }
        }
        return ActionPlan.command(createOrderCommand(action, calculated, state, dealContext, tranche));
    }

    /**
     * Параметры создания несут и <b>плановый снимок риска ноги</b>: под
     * какую цену входа, какой стоп и какой размер контракта считался её
     * риск. Снимок собирается ЗДЕСЬ, потому что здесь он и вычислен —
     * исполнитель команды расчёта не повторяет, а восстановить его потом
     * было бы нечем: цены и правила инструмента к тому времени уедут.
     */
    private ServiceCommand createOrderCommand(StrategyOrderAction action, CalculatedStrategyAction calculated,
                                              DealActionState state, DealContext dealContext,
                                              DealTranche tranche) {
        String instId = dealContext.getInstrument().getExternalId();
        CreateOrderCommandPayload payload = CreateOrderCommandPayload.builder()
                .orderType(action.getOrderType())
                .strategyDirection(action.getDirection())
                .side(toSide(action.getDirection()))
                .instrumentExternalId(instId)
                .sizeContracts(calculated.getCalculatedSize().getSizeContracts())
                .price(calculated.getCalculatedPrice().getRoundedPrice())
                .sendPriceToExchange(calculated.getCalculatedPrice().getSendPriceToExchange())
                .positionReducingOnly(action.getPositionReducingOnly())
                .dealTrancheId(nonNull(tranche) ? tranche.getId() : null)
                .plannedEntryPrice(calculated.getCalculatedPrice().getRoundedPrice())
                .plannedStopPrice(stopPriceOf(calculated))
                .plannedRiskAmount(plannedRiskOf(action, calculated, dealContext))
                .plannedRiskCurrency(dealContext.getInstrument().getExternalSettlementCurrency())
                .plannedContractValue(contractValueOf(dealContext))
                .build();
        return command(ServiceCommandType.CREATE_ORDER_COMMAND, dealContext, state, payload);
    }

    /**
     * Плановый риск ноги — та же закрытая форма, что у чисел риска
     * сделки: разведены не формулы, а операнды. Ставка читается СВОЕЙ
     * тропой через границу хранилища навеса — той же, какой её читает
     * преконтроль (docs/components/RiskValidator.md).
     */
    private BigDecimal plannedRiskOf(StrategyOrderAction action, CalculatedStrategyAction calculated,
                                     DealContext dealContext) {
        return DealRiskNumbers.plannedRisk(action.getDirection(),
                calculated.getCalculatedPrice().getRoundedPrice(), stopPriceOf(calculated),
                feeRateOf(dealContext), calculated.getCalculatedSize().getSizeContracts(),
                contractValueOf(dealContext));
    }

    private BigDecimal contractValueOf(DealContext dealContext) {
        return rulesDataService.findByInstrumentId(dealContext.getInstrument().getId())
                .map(InstrumentExternalRules::contractValue)
                .orElse(null);
    }

    private BigDecimal feeRateOf(DealContext dealContext) {
        return rulesDataService.findByInstrumentId(dealContext.getInstrument().getId())
                .map(InstrumentExternalRules::takerFeeRate)
                .orElse(null);
    }

    /** Уровень стопа рассчитанного действия; пусто — стопа у него нет. */
    private BigDecimal stopPriceOf(CalculatedStrategyAction calculated) {
        return isNull(calculated.getCalculatedPrice().getStopLossPrice())
                ? null
                : calculated.getCalculatedPrice().getStopLossPrice().getTriggerPrice();
    }

    private ActionPlan submitCommand(DealActionState state, DealContext dealContext) {
        Long targetEntityId = state.getTargetEntityId();
        if (isNull(targetEntityId)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.SUBMIT_ORDER_COMMAND, dealContext, state,
                new SubmitOrderCommandPayload(targetEntityId)));
    }

    private ActionPlan refreshCommand(DealActionState state, DealContext dealContext) {
        Long targetEntityId = state.getTargetEntityId();
        if (isNull(targetEntityId)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.REFRESH_ORDER_COMMAND, dealContext, state,
                new RefreshOrderCommandPayload(targetEntityId)));
    }

    private ServiceCommand command(ServiceCommandType type, DealContext dealContext, DealActionState state,
                                   ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(dealContext.getDeal().getId())
                .dealActionStateId(state.getId())
                .payload(payload)
                .build();
    }

    /** Risk-creating действие — открывающий/наращивающий (не reduce-only) ордер. */
    private Boolean isRiskCreating(StrategyOrderAction action) {
        return isNotTrue(action.getPositionReducingOnly());
    }

    /**
     * Направление стратегии → сторона заявки. Оба перечня доменные:
     * литерала площадки на этой тропе больше нет, перевод в её словарь
     * делает маппер на границе (docs/models/mapping/Order.md).
     */
    private Order.Side toSide(StrategyTradeDirection direction) {
        return StrategyTradeDirection.LONG.equals(direction) ? Order.Side.BUY : Order.Side.SELL;
    }
}
