package com.example.tradingbot.domain.command;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingbot.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.SubmitAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.persistence.service.DealFinalizationStateDataService;
import com.example.tradingbot.util.Constants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Создаёт одну актуальную ServiceCommand из рассчитанного действия по
 * стадии DealActionState («одна команда за проход», не вся цепочка):
 * PLANNED → CREATE_* / CLOSE_POSITION; CREATED → SUBMIT_*; SUBMITTED →
 * REFRESH_*. Условия/цену/риск не считает (вход уже рассчитан и
 * risk-validated). CANCEL-резолюция цели по цепочке, REPLACE-оркестрация
 * ног и полные SL/TP-цены algo — refinement (граница со step-5
 * калькулятором). См. docs/components/ServiceCommandFactory.md.
 */
@Service
@RequiredArgsConstructor
public class ServiceCommandFactory {

    private final DealFinalizationStateDataService dealFinalizationStateDataService;

    public Optional<ServiceCommand> nextCommand(CalculatedStrategyAction calculated, DealActionState actionState,
                                                DealContext dealContext) {
        if (isNull(actionState) || DealActionStateStatus.PLANNED.equals(actionState.getStatus())) {
            return initialCommand(calculated, actionState, dealContext);
        }
        return switch (actionState.getStatus()) {
            case CREATED -> submitCommand(actionState, dealContext);
            case SUBMITTED -> refreshCommand(actionState, dealContext);
            default -> Optional.empty();
        };
    }

    /**
     * Эмитит одну финализационную команду (FINALIZE_DEAL_* / MARK_DEAL_*) по
     * статусу DealFinalizationState(deal, type): отсутствует/PENDING →
     * команда; RETRY_PENDING → команда, только если наступил nextRetryAt
     * (иначе ждём backoff); COMPLETED → empty (сделано); FAILED → empty
     * (исчерпано — сделку на ошибочную тропу выводит handler, DEAL-Q2).
     * Материализует строку (upsert по UNIQUE(deal_id, type)) при первом
     * обращении. Команда привязывается к dealFinalizationStateId.
     */
    public Optional<ServiceCommand> finalizationCommand(DealFinalizationType type, DealContext dealContext) {
        Long dealId = dealContext.getDeal().getId();
        DealFinalizationState existing = dealFinalizationStateDataService.findByDealIdAndType(dealId, type).orElse(null);
        if (nonNull(existing) && isFalse(retryDue(existing))) {
            return Optional.empty();
        }
        DealFinalizationState state = nonNull(existing) ? existing : createPending(dealId, type);
        return Optional.of(finalizationServiceCommand(type, dealId, state.getId()));
    }

    /** Команду по финализации можно эмитить: PENDING — да; RETRY_PENDING — по наступлении nextRetryAt; COMPLETED/FAILED — нет. */
    private Boolean retryDue(DealFinalizationState state) {
        return switch (state.getStatus()) {
            case PENDING -> true;
            case RETRY_PENDING -> isNull(state.getNextRetryAt())
                    || isFalse(OffsetDateTime.now(ZoneOffset.UTC).isBefore(state.getNextRetryAt()));
            case COMPLETED, FAILED -> false;
        };
    }

    private DealFinalizationState createPending(Long dealId, DealFinalizationType type) {
        DealFinalizationState state = new DealFinalizationState();
        state.setDealId(dealId);
        state.setType(type);
        state.setStatus(DealFinalizationStateStatus.PENDING);
        return dealFinalizationStateDataService.save(state);
    }

    private ServiceCommand finalizationServiceCommand(DealFinalizationType type, Long dealId,
                                                      Long finalizationStateId) {
        return ServiceCommand.builder()
                .type(toCommandType(type))
                .dealId(dealId)
                .dealFinalizationStateId(finalizationStateId)
                .build();
    }

    private ServiceCommandType toCommandType(DealFinalizationType type) {
        return switch (type) {
            case FINALIZE_ENTRY -> ServiceCommandType.FINALIZE_DEAL_ENTRY;
            case FINALIZE_EXIT -> ServiceCommandType.FINALIZE_DEAL_EXIT;
            case MARK_CLOSED -> ServiceCommandType.MARK_DEAL_CLOSED;
            case MARK_ERROR -> ServiceCommandType.MARK_DEAL_ERROR;
        };
    }

    private Optional<ServiceCommand> initialCommand(CalculatedStrategyAction calculated, DealActionState actionState,
                                                    DealContext dealContext) {
        StrategyAction action = calculated.getSourceAction();
        return switch (action.getActionType()) {
            case CREATE -> createCommand(calculated, actionState, dealContext);
            case CLOSE_FULL -> closeCommand(actionState, dealContext);
            case CANCEL, REPLACE -> Optional.empty();
        };
    }

    private Optional<ServiceCommand> createCommand(CalculatedStrategyAction calculated, DealActionState actionState,
                                                   DealContext dealContext) {
        StrategyAction action = calculated.getSourceAction();
        String instId = dealContext.getInstrument().getExternalId();
        Long dealId = dealContext.getDeal().getId();
        Long actionStateId = actionStateId(actionState);
        if (action instanceof StrategyOrderAction orderAction) {
            return Optional.of(command(ServiceCommandType.CREATE_ORDER, dealId, actionStateId,
                    createOrderPayload(orderAction, calculated, instId)));
        }
        if (action instanceof StrategyAlgoOrderAction algoAction) {
            return Optional.of(command(ServiceCommandType.CREATE_ALGO_ORDER, dealId, actionStateId,
                    createAlgoPayload(algoAction, calculated, instId, dealContext)));
        }
        return Optional.empty();
    }

    private CreateOrderCommandPayload createOrderPayload(StrategyOrderAction action, CalculatedStrategyAction calc,
                                                         String instId) {
        return CreateOrderCommandPayload.builder()
                .orderType(action.getOrderType())
                .strategyDirection(action.getDirection())
                .side(toSide(action.getDirection()))
                .instrumentExternalId(instId)
                .sizeContracts(calc.getCalculatedSize().getSizeContracts())
                .price(calc.getCalculatedPrice().getRoundedPrice())
                .sendPriceToExchange(calc.getCalculatedPrice().getSendPriceToExchange())
                .positionReducingOnly(action.getPositionReducingOnly())
                .build();
    }

    private CreateAlgoOrderCommandPayload createAlgoPayload(StrategyAlgoOrderAction action, CalculatedStrategyAction calc,
                                                            String instId, DealContext dealContext) {
        Condition condition = new Condition();
        condition.setType(action.getConditionType());
        return CreateAlgoOrderCommandPayload.builder()
                .conditionType(action.getConditionType())
                .direction(closeDirection(dealContext.getDeal().getDirection()))
                .instrumentExternalId(instId)
                .positionReducingOnly(Boolean.TRUE)
                .sizeContracts(calc.getCalculatedSize().getSizeContracts())
                .condition(condition)
                .build();
    }

    private Optional<ServiceCommand> closeCommand(DealActionState actionState, DealContext dealContext) {
        Position position = dealContext.getDeal().getPosition();
        if (isNull(position)) {
            return Optional.empty();
        }
        ClosePositionCommandPayload payload =
                new ClosePositionCommandPayload(position.getId(), Position.CloseReason.CLOSED_BY_STRATEGY);
        return Optional.of(command(ServiceCommandType.CLOSE_POSITION, dealContext.getDeal().getId(),
                actionStateId(actionState), payload));
    }

    private Optional<ServiceCommand> submitCommand(DealActionState actionState, DealContext dealContext) {
        RuntimeTarget target = actionState.getTarget();
        if (isNull(target)) {
            return Optional.empty();
        }
        Long dealId = dealContext.getDeal().getId();
        Long actionStateId = actionState.getId();
        return switch (target.getEntityType()) {
            case ORDER -> Optional.of(command(ServiceCommandType.SUBMIT_ORDER, dealId, actionStateId,
                    new SubmitOrderCommandPayload(target.getEntityId())));
            case ALGO_ORDER -> Optional.of(command(ServiceCommandType.SUBMIT_ALGO_ORDER, dealId, actionStateId,
                    new SubmitAlgoOrderCommandPayload(target.getEntityId())));
            default -> Optional.empty();
        };
    }

    private Optional<ServiceCommand> refreshCommand(DealActionState actionState, DealContext dealContext) {
        RuntimeTarget target = actionState.getTarget();
        if (isNull(target)) {
            return Optional.empty();
        }
        Long dealId = dealContext.getDeal().getId();
        Long actionStateId = actionState.getId();
        return switch (target.getEntityType()) {
            case ORDER -> Optional.of(command(ServiceCommandType.REFRESH_ORDER, dealId, actionStateId,
                    new RefreshOrderCommandPayload(target.getEntityId())));
            case ALGO_ORDER -> Optional.of(command(ServiceCommandType.REFRESH_ALGO_ORDER, dealId, actionStateId,
                    new RefreshAlgoOrderCommandPayload(target.getEntityId())));
            case POSITION -> Optional.of(command(ServiceCommandType.REFRESH_POSITION, dealId, actionStateId, null));
            default -> Optional.empty();
        };
    }

    private ServiceCommand command(ServiceCommandType type, Long dealId, Long actionStateId,
                                   ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(dealId)
                .dealActionStateId(actionStateId)
                .payload(payload)
                .build();
    }

    private String toSide(StrategyTradeDirection direction) {
        return StrategyTradeDirection.LONG.equals(direction) ? Constants.Okx.SIDE_BUY : Constants.Okx.SIDE_SELL;
    }

    private AlgoOrder.Direction closeDirection(StrategyTradeDirection dealDirection) {
        return StrategyTradeDirection.LONG.equals(dealDirection) ? AlgoOrder.Direction.SELL : AlgoOrder.Direction.BUY;
    }

    private Long actionStateId(DealActionState actionState) {
        return nonNull(actionState) ? actionState.getId() : null;
    }
}
