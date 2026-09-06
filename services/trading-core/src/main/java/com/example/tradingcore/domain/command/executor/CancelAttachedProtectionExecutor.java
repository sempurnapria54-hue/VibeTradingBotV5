package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CancelAttachedProtectionCommandPayload;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Снимает ВСТРОЕННУЮ защиту — раздел модели заявки
 * (docs/models/domain/core/Order.md §«Встроенная защита»), а не отдельную
 * условную заявку.
 *
 * <p><b>Своя команда, а не адресат в чужой:</b> цель — другая сущность, и
 * словарь причин у неё непересекающийся
 * (docs/components/models/ServiceCommand.md).
 *
 * <p><b>Адресация на площадке:</b> снимаемая защита к этому моменту
 * материализована — обе тропы эмиссии наступают при живой позиции, а
 * живая позиция означает непустой налив родителя. Инструмент берётся у
 * родительской заявки; форму запроса держит коннектор
 * (docs/components/CancelAttachedProtectionExecutor.md §«Адресация на
 * бирже»).
 *
 * <p><b>Снятое состояние по подтверждению приёма не ставит</b>, чисел
 * риска не пересчитывает: команда записывает намерение, а операнд
 * «действующая защита» двигает наблюдённый факт.
 */
@Component
@RequiredArgsConstructor
public class CancelAttachedProtectionExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CancelAttachedProtectionCommandPayload payload =
                (CancelAttachedProtectionCommandPayload) command.getPayload();
        AttachedAlgoOrder attached =
                orderDataService.getRequiredAttachedById(payload.getAttachedAlgoOrderId());
        // Инструмент родительской заявки и инструмент прохода — один и тот
        // же: своего поля инструмента нога не несёт, а сделка на паре «счёт,
        // инструмент» активна ровно одна
        // (docs/models/domain/aggregate/Deal.md).
        ExchangeAck ack = exchangeOperationsClient.cancelAttachedProtection(
                dealContext.getExchangeAccount().getInternalId(), attached,
                dealContext.getInstrument().getExternalId());
        if (isFalse(ack.getSuccess())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
        }
        applyIntent(attached, payload.getCancelReason());
        markSubmitted(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Намерение снятия — write-once: повтор до подтверждения факта
     * отправляет снятие снова, а причину не перезаписывает. Приём снятия
     * состоянием не является.
     */
    private void applyIntent(AttachedAlgoOrder attached, AttachedAlgoOrder.CloseReason cancelReason) {
        if (isNull(attached.getCloseReason())) {
            attached.setCloseReason(cancelReason);
            orderDataService.saveAttached(attached);
        }
    }

    private void markSubmitted(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.SUBMITTED);
            dealActionStateDataService.save(actionState);
        }
    }
}
