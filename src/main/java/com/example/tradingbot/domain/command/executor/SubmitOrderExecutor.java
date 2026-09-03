package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.integration.service.ExchangeIntegrationException;
import com.example.tradingbot.integration.service.IntegrationService;
import java.time.OffsetDateTime;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет SUBMIT_ORDER: загружает локальный Order; если externalId
 * пуст — перед повторным submit ищет факт по stable client id (D-B3:
 * place мог реально пройти, ответ потерян — не плодим дубль), иначе
 * выставляет рабочее плечо (для открывающего ордера, idempotent) и
 * отправляет на биржу; на successful ACK сохраняет externalId + PENDING;
 * обновляет DealActionState = SUBMITTED. ACK не runtime truth — факт
 * подтверждает REFRESH_ORDER. См. docs/components/SubmitOrderExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class SubmitOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealDataService dealDataService;
    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.SUBMIT_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        SubmitOrderCommandPayload payload = (SubmitOrderCommandPayload) command.getPayload();
        Order order = orderDataService.getRequiredById(payload.getOrderId());
        String instId = dealContext.getInstrument().getExternalId();
        if (isBlank(order.getExternalId())
                && isFalse(recoverByClientId(order, instId, actionState, command.getDealId()))) {
            ensureLeverage(order, dealContext);
            ExchangeAck ack = integrationService.placeOrder(order, instId);
            if (isFalse(ack.getSuccess())) {
                return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
            }
            order.setExternalId(ack.getExternalId());
            order.setStatus(Order.Status.PENDING);
            markProtectionPending(order);
            orderDataService.save(order);
            applyBillsWindowBegin(order, command.getDealId(), ack.getExternalCreatedAt());
        }
        actionState.setStatus(DealActionStateStatus.SUBMITTED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * D-B3: только перед ПОВТОРНЫМ submit ищем ордер по stable client id —
     * предыдущий place мог реально выполниться, даже если ответ не получен.
     * Найден → восстанавливаем externalId, второй раз не отправляем.
     */
    private Boolean recoverByClientId(Order order, String instId, DealActionState actionState, Long dealId) {
        if (isFalse(isRetry(actionState))) {
            return false;
        }
        OrderExternalSnapshot existing = integrationService.getOrder(instId, null, order.getInternalId());
        if (isNull(existing) || isBlank(existing.getExternalId())) {
            return false;
        }
        order.setExternalId(existing.getExternalId());
        order.setStatus(Order.Status.PENDING);
        markProtectionPending(order);
        orderDataService.save(order);
        applyBillsWindowBegin(order, dealId, existing.getExternalCreatedAt());
        return true;
    }

    /**
     * Нижняя граница окна линковки движений: биржевое время первой
     * отправленной ВХОДНОЙ заявки сделки, каким бы траншем она ни
     * ставилась (docs/models/domain/aggregate/Deal.md). Write-once
     * держит охрана запроса; reduce-only заявки границу не пишут, пустое
     * биржевое время не фабрикуется — граница остаётся суррогату
     * (docs/spec/cash-flow-linkage.json §lowerBound).
     */
    private void applyBillsWindowBegin(Order order, Long dealId, OffsetDateTime observedAt) {
        if (isTrue(order.getPositionReducingOnly()) || isNull(dealId) || isNull(observedAt)) {
            return;
        }
        dealDataService.applyBillsWindowBegin(dealId, observedAt);
    }

    /**
     * Встроенная защита уходит на биржу вместе с родителем (attachAlgoOrds
     * place-запроса), поэтому факт «родитель отправлен» пишется и ей:
     * CREATED → PENDING (docs/lifecycles/Order.md §«Состояния
     * AttachedAlgoOrder»). Живость этим не утверждается — её подтверждает
     * REFRESH-контур фактом материализации.
     */
    private void markProtectionPending(Order order) {
        if (isEmpty(order.getAttachedAlgoOrders())) {
            return;
        }
        for (AttachedAlgoOrder attached : order.getAttachedAlgoOrders()) {
            if (isTrue(attached.canTransitionTo(AttachedAlgoOrder.Status.PENDING))) {
                attached.toPending();
            }
        }
    }

    /** Рабочее плечо на бирже перед постановкой открывающего ордера (idempotent); reduce-only не трогаем. */
    private void ensureLeverage(Order order, DealContext dealContext) {
        if (isTrue(order.getPositionReducingOnly())) {
            return;
        }
        Integer leverage = dealContext.getInstrument().getLeverage();
        if (isNull(leverage)) {
            return;
        }
        String instId = dealContext.getInstrument().getExternalId();
        ExchangeAck ack = integrationService.setLeverage(instId, leverage);
        if (isFalse(ack.getSuccess())) {
            throw new ExchangeIntegrationException("set-leverage rejected instId=" + instId);
        }
    }

    private Boolean isRetry(DealActionState actionState) {
        return nonNull(actionState) && nonNull(actionState.getAttemptCount()) && actionState.getAttemptCount() > 0;
    }
}
