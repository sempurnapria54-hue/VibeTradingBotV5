package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.integration.exchange.ExchangeIntegrationException;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отправляет обычную заявку на площадку.
 *
 * <p>Внешний идентификатор есть — команда уже выполнена. Пуст —
 * <b>ищет заявку по стабильному клиентскому идентификатору</b>: найдена —
 * восстанавливает факт отправки, не найдена — отправляет
 * (docs/components/SubmitOrderExecutor.md).
 *
 * <p><b>Восстановимость:</b> упади приложение после отправки, но до
 * сохранения внешнего идентификатора, следующая отправка найдёт заявку по
 * клиентскому идентификатору. Подтверждение приёма состоянием не считается
 * (docs/rules/ack-not-runtime-truth.md): факт подтверждает добыча.
 */
@Component
@RequiredArgsConstructor
public class SubmitOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealDataService dealDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.SUBMIT_ORDER_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        SubmitOrderCommandPayload payload = (SubmitOrderCommandPayload) command.getPayload();
        Order order = orderDataService.getRequiredById(payload.getOrderId());
        String accountInternalId = dealContext.getExchangeAccount().getInternalId();
        Instrument instrument = dealContext.getInstrument();
        if (isBlank(order.getExternalId())
                && isFalse(recoverByClientId(order, accountInternalId, instrument, actionState))) {
            ensureLeverage(order, dealContext, accountInternalId, instrument);
            ExchangeAck ack = exchangeOperationsClient.placeOrder(accountInternalId, order,
                    instrument.getExternalId());
            if (isFalse(ack.getSuccess())) {
                return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
            }
            applySubmitted(order, ack.getExternalId(), ack.getExternalCreatedAt());
        }
        actionState.setStatus(DealActionStateStatus.SUBMITTED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Только перед ПОВТОРНОЙ отправкой ищем заявку по стабильному
     * клиентскому идентификатору: предыдущая постановка могла реально
     * пройти, даже если ответ не получен. Найдена — восстанавливаем факт
     * отправки и второй раз не шлём.
     */
    private Boolean recoverByClientId(Order order, String accountInternalId, Instrument instrument,
                                      DealActionState actionState) {
        if (isFalse(isRetry(actionState))) {
            return false;
        }
        Order existing = exchangeOperationsClient.getOrder(accountInternalId, instrument.getExternalId(),
                null, order.getInternalId());
        if (isNull(existing) || isBlank(existing.getExternalId())) {
            return false;
        }
        applySubmitted(order, existing.getExternalId(), existing.getExternalCreatedAt());
        return true;
    }

    /** Факт отправки: биржевой идентификатор, статус ноги и её встроенной защиты, граница окна. */
    private void applySubmitted(Order order, String externalId, OffsetDateTime observedAt) {
        order.setExternalId(externalId);
        order.setStatus(Order.Status.PENDING);
        markProtectionPending(order);
        orderDataService.save(order);
        applyBillsWindowBegin(order, observedAt);
    }

    /**
     * Нижняя граница окна линковки движений: биржевое время <b>первой
     * отправленной входной заявки</b> сделки, каким бы траншем она ни
     * ставилась. <b>Единственный писатель — этот исполнитель</b>, и он
     * пишет её безусловно на всех тропах
     * (docs/models/domain/aggregate/Deal.md).
     *
     * <p>Write-once держит охрана самого запроса: уже заполненная граница
     * не перетирается последующими постановками. Заявка, только
     * уменьшающая позицию, границы не пишет — окно открывает вход; пустое
     * биржевое время не фабрикуется, граница остаётся суррогату
     * (docs/spec/cash-flow-linkage.json).
     */
    private void applyBillsWindowBegin(Order order, OffsetDateTime observedAt) {
        if (isTrue(order.getPositionReducingOnly()) || isNull(order.getDealId()) || isNull(observedAt)) {
            return;
        }
        dealDataService.applyBillsWindowBegin(order.getDealId(), observedAt);
    }

    /**
     * Встроенная защита уходит на площадку вместе с родителем, поэтому
     * факт «родитель отправлен» пишется и ей. Живость этим не
     * утверждается — её подтверждает добыча фактом материализации
     * (docs/lifecycles/Order.md).
     */
    private void markProtectionPending(Order order) {
        if (isEmpty(order.getAttachedAlgoOrders())) {
            return;
        }
        order.getAttachedAlgoOrders().stream()
                .filter(attached -> isTrue(attached.canTransitionTo(AttachedAlgoOrder.Status.PENDING)))
                .forEach(AttachedAlgoOrder::toPending);
    }

    /**
     * Рабочее плечо пишется на площадку ПЕРЕД постановкой открывающей
     * заявки — здесь, а не при заведении инструмента: значение статично, а
     * применить его нужно к моменту входа. Операция идемпотентна; заявка,
     * только уменьшающая позицию, плеча не трогает.
     *
     * <p><b>Значение читается со строки ПАРЫ «счёт, инструмент», а не с
     * проекции каталога.</b> Плечо объявлено настройкой СЧЁТА НА
     * ИНСТРУМЕНТЕ (docs/architecture/tenant-and-exchange.md §Инструменты),
     * и колонки под него у проекции нет вовсе: её перезаписывает синк, и
     * запись ядра он затирал бы каждым тиком. Прежняя редакция читала
     * {@code Instrument.leverage} — поле, которое у ядра пусто ПО
     * ПОСТРОЕНИЮ, то есть плечо не выставлялось никогда и молча.
     */
    private void ensureLeverage(Order order, DealContext dealContext, String accountInternalId,
                                Instrument instrument) {
        if (isTrue(order.getPositionReducingOnly())) {
            return;
        }
        Integer leverage = accountInstrumentStateDataService
                .getRequiredByPair(dealContext.getExchangeAccount().getId(), instrument.getId())
                .getLeverage();
        if (isNull(leverage)) {
            return;
        }
        ExchangeAck ack = exchangeOperationsClient.setLeverage(accountInternalId, instrument.getExternalId(),
                leverage);
        if (isFalse(ack.getSuccess())) {
            throw new ExchangeIntegrationException(
                    "Leverage rejected for instrument " + instrument.getExternalId() + ": " + ack.getMessage());
        }
    }

    private Boolean isRetry(DealActionState actionState) {
        return nonNull(actionState) && nonNull(actionState.getAttemptCount())
                && actionState.getAttemptCount() > 0;
    }
}
