package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.command.payload.AttachedProtectionPayload;
import com.example.tradingcore.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создаёт локальную ногу входа: клиентский идентификатор, рассчитанные
 * параметры, встроенная защита внутри заявки, цель строки исполнения и её
 * статус — <b>всё одной транзакцией</b>. На площадку не ходит, цену не
 * пересчитывает, условия не проверяет
 * (docs/components/CreateOrderExecutor.md).
 *
 * <p><b>Анкер идемпотентности — строка исполнения.</b> Повтор до
 * завершения работает с ТОЙ ЖЕ локальной сущностью по тому же клиентскому
 * идентификатору: заново сгенерированный идентификатор оставил бы сироту,
 * а на площадку она уехала бы вторым размером.
 */
@Component
@RequiredArgsConstructor
public class CreateOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealDataService dealDataService;
    private final DealRiskNumbersService dealRiskNumbersService;
    private final CoreEventWriter coreEventWriter;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CREATE_ORDER_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CreateOrderCommandPayload payload = (CreateOrderCommandPayload) command.getPayload();
        Order order = resolveTarget(actionState, payload, dealContext);
        actionState.targetAt(TargetEntityType.ORDER, order.getId());
        actionState.setStatus(DealActionStateStatus.CREATED);
        dealActionStateDataService.save(actionState);
        freezeRiskBase(payload, dealContext);
        appendToGraph(dealContext.getDeal(), order);
        if (isFalse(dealRiskNumbersService.recompute(dealContext))) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Deal graph incomplete: risk numbers not recomputed for order " + order.getId());
        }
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Локальная сущность звена: уже заведённая либо новая. Цель строки
     * исполнения и есть анкер повтора — по ней сущность и находится.
     */
    private Order resolveTarget(DealActionState actionState, CreateOrderCommandPayload payload,
                                DealContext dealContext) {
        if (nonNull(actionState.getTargetEntityId())
                && TargetEntityType.ORDER.equals(actionState.getTargetEntityType())) {
            return orderDataService.getRequiredById(actionState.getTargetEntityId());
        }
        Order saved = orderDataService.save(buildOrder(payload, dealContext.getDeal().getId()));
        publishDecided(dealContext, saved);
        return saved;
    }

    /**
     * Событие решения о заявке — <b>той же транзакцией</b>, что заводит её
     * строку и присваивает клиентский идентификатор
     * (docs/architecture/contracts.md §«У каждого класса события назван
     * писатель, и он же писатель решения»).
     *
     * <p><b>Повтор звена события не производит:</b> строка уже заведена, и
     * решение принято однажды. Иначе один и тот же ордер приезжал бы
     * потребителю столько раз, сколько было попыток отправки.
     *
     * <p><b>Идентичность транша читается из графа прохода</b>: транши в нём
     * уже загружены, и чтение строки транша ради одного поля было бы
     * запросом по прочитанному. Без неё заявки при нескольких уровнях входа
     * к траншам неатрибутируемы
     * (docs/architecture/contracts.md §«Решение о заявке несёт идентичность
     * транша»).
     */
    private void publishDecided(DealContext dealContext, Order order) {
        Deal deal = dealContext.getDeal();
        coreEventWriter.orderDecided(dealContext.getExchangeAccount().getTenantId(), order,
                deal.getInternalId(), deal.trancheInternalId(order.getDealTrancheId()),
                dealContext.getExchangeAccount().getInternalId(),
                dealContext.getInstrument().getInternalId());
    }

    /**
     * База риска и валюта риска сделки — write-once снимки момента ПЕРВОГО
     * сайзинга, а не производные графа: они пишутся одним ходом при
     * заведении ноги и пересчёту не подлежат.
     *
     * <p>Значением снимок и живая база совпадают ровно в момент заморозки;
     * расходятся они потом, и с этого момента потолки живой сделки
     * считаются от снимка — заморозка внутри сделки и есть смысл поля
     * (docs/rules/risk-policy.md).
     *
     * <p><b>Охрана write-once стои́т в самом запросе</b>, а не здесь:
     * оставленная вызывающему, она держится ровно до второго вызывающего.
     * На модель значение при этом кладётся тоже — граф прохода читают
     * звенья после нас.
     */
    private void freezeRiskBase(CreateOrderCommandPayload payload, DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        BigDecimal accountBase = nonNull(dealContext.getExchangeAccount())
                ? dealContext.getExchangeAccount().getRiskBase()
                : null;
        if (isNull(deal.getPlannedRiskEquityBase()) && nonNull(accountBase)) {
            deal.setPlannedRiskEquityBase(accountBase);
            dealDataService.applyPlannedRiskEquityBase(deal.getId(), accountBase);
        }
        if (isNull(deal.getPlannedRiskCurrency()) && nonNull(payload.getPlannedRiskCurrency())) {
            deal.setPlannedRiskCurrency(payload.getPlannedRiskCurrency());
            dealDataService.applyPlannedRiskCurrency(deal.getId(), payload.getPlannedRiskCurrency());
        }
    }

    /**
     * Свежесозданная нога входит в граф прохода ТОЙ ЖЕ транзакцией:
     * контекст собран до неё, и без этого числа риска считались бы по
     * графу без только что заведённой ноги — ровно на её величину
     * заниженными.
     *
     * <p>Нога кладётся <b>на свой транш</b>, а не в поле агрегата: в
     * целевой модели ноги висят на траншах и собираются их обходом
     * (docs/models/domain/aggregate/Deal.md §Структура).
     */
    private void appendToGraph(Deal deal, Order saved) {
        emptyIfNull(deal.getTranches()).stream()
                .filter(tranche -> Objects.equals(saved.getDealTrancheId(), tranche.getId()))
                .forEach(tranche -> {
                    List<Order> own = new ArrayList<>(emptyIfNull(tranche.getOrders()));
                    if (own.stream().noneMatch(order -> Objects.equals(saved.getId(), order.getId()))) {
                        own.add(saved);
                        tranche.setOrders(own);
                    }
                });
    }

    private Order buildOrder(CreateOrderCommandPayload payload, Long dealId) {
        Order order = new Order();
        order.setDealId(dealId);
        order.setDealTrancheId(payload.getDealTrancheId());
        order.setInternalId(InternalIdFactory.forExchangeBoundEntity());
        order.setStatus(Order.Status.CREATED);
        order.setType(payload.getOrderType());
        order.setSide(payload.getSide());
        order.setSize(payload.getSizeContracts());
        order.setPositionReducingOnly(payload.getPositionReducingOnly());
        if (isTrue(payload.getSendPriceToExchange())) {
            order.setPrice(payload.getPrice());
        }
        order.setAttachedAlgoOrders(buildAttached(payload.getAttachedProtection()));
        applyPlannedRisk(order, payload);
        return order;
    }

    /**
     * Шесть чисел планового риска ноги — write-once снимок момента
     * постановки: «под какой риск сайзились» отвечает тогдашним
     * состоянием, а не сегодняшним, поэтому они не пересчитываются.
     *
     * <p>Два измерителя рядом с ними в инвариант «шесть или ни одного» НЕ
     * входят: их операнды наблюдаются не всегда, и пустота у них —
     * самостоятельное значение «не измеряли»
     * (docs/models/domain/core/Order.md).
     */
    private void applyPlannedRisk(Order order, CreateOrderCommandPayload payload) {
        order.setPlannedEntryPrice(payload.getPlannedEntryPrice());
        order.setPlannedSizeContracts(payload.getSizeContracts());
        order.setPlannedRiskAmount(payload.getPlannedRiskAmount());
        order.setPlannedRiskCurrency(payload.getPlannedRiskCurrency());
        order.setPlannedContractValue(payload.getPlannedContractValue());
        order.setPlannedStopPrice(payload.getPlannedStopPrice());
        order.setLiquidationDistanceRatio(payload.getLiquidationDistanceRatio());
        order.setBookDepthAtPlacement(payload.getBookDepthAtPlacement());
    }

    /**
     * Встроенная защита создаётся внутри заявки и уходит на площадку
     * ВМЕСТЕ с ней. Ценовая база триггера — объявленная стратегией; без
     * неё вход не выпускается (docs/components/CreateOrderExecutor.md
     * §«Встроенная защита»).
     */
    private List<AttachedAlgoOrder> buildAttached(AttachedProtectionPayload protection) {
        if (isNull(protection)) {
            return null;
        }
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setInternalId(InternalIdFactory.forExchangeBoundEntity());
        attached.setStatus(AttachedAlgoOrder.Status.CREATED);
        attached.setType(protection.getAttachedType());
        attached.setStopLossTriggerPrice(protection.getStopLossTriggerPrice());
        attached.setTriggerPriceType(protection.getTriggerPriceType());
        attached.setSize(protection.getSize());
        return List.of(attached);
    }
}
