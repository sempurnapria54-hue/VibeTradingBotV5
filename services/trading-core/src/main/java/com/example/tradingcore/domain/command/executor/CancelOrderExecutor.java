package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Снимает обычную заявку: отправляет снятие и фиксирует причину write-once.
 *
 * <p><b>В снятое состояние по подтверждению приёма не переводит</b> — факт
 * снятия подтверждает добыча (docs/rules/ack-not-runtime-truth.md).
 * Причина закрытия не перетирается, если уже установлена: она и есть наше
 * стоящее НАМЕРЕНИЕ, из которого разбор берёт причину найденной снятой
 * записи (docs/components/CancelOrderExecutor.md).
 *
 * <p><b>Четвёрку чисел риска не пересчитывает:</b> команда записывает
 * намерение, а операнд «живость ноги» двигает наблюдённый факт
 * (docs/models/domain/aggregate/Deal.md §«Писатели четвёрки и их
 * триггеры»).
 *
 * <p><b>Строка исполнения у дочистки пуста</b>, и это штатно: отмены
 * эмитируются напрямую, без анкера, — бюджета отказов у них нет
 * (docs/components/models/ServiceCommand.md).
 */
@Component
@RequiredArgsConstructor
public class CancelOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CANCEL_ORDER_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CancelOrderCommandPayload payload = (CancelOrderCommandPayload) command.getPayload();
        Order order = orderDataService.getRequiredById(payload.getOrderId());
        ExchangeAck ack = exchangeOperationsClient.cancelOrder(
                dealContext.getExchangeAccount().getInternalId(), order,
                dealContext.getInstrument().getExternalId());
        if (isFalse(ack.getSuccess())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
        }
        applyIntent(order, payload.getCancelReason());
        markSubmitted(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /** Намерение снятия — write-once: наблюдение его не перезаписывает. */
    private void applyIntent(Order order, Order.CloseReason cancelReason) {
        if (isNull(order.getCloseReason())) {
            order.setCloseReason(cancelReason);
            orderDataService.save(order);
        }
    }

    private void markSubmitted(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.SUBMITTED);
            dealActionStateDataService.save(actionState);
        }
    }
}
