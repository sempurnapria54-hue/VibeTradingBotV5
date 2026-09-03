package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.TargetEntityType;
import com.example.tradingbot.domain.command.payload.AttachedProtectionPayload;
import com.example.tradingbot.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingbot.domain.command.risk.DealRiskNumbers;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет CREATE_ORDER: создаёт локальный Order (CREATED) с
 * сгенерированным internalId и рассчитанными параметрами, attached
 * protection (если есть), обновляет DealActionState (target = ORDER,
 * status = CREATED) — всё одной транзакцией. На биржу не ходит. См.
 * docs/components/CreateOrderExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class CreateOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealDataService dealDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CREATE_ORDER_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CreateOrderCommandPayload payload = (CreateOrderCommandPayload) command.getPayload();
        Order saved = orderDataService.save(buildOrder(payload, dealContext.getDeal().getId()));
        actionState.targetAt(TargetEntityType.ORDER, saved.getId());
        actionState.setStatus(DealActionStateStatus.CREATED);
        dealActionStateDataService.save(actionState);
        applyRiskNumbers(payload, saved, dealContext);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Заведение ноги меняет операнд четвёрки чисел риска сделки —
     * множество входных ног, — поэтому исполнитель ЯВЛЯЕТСЯ писателем
     * четвёрки и пересчитывает её целиком той же транзакцией
     * (docs/models/domain/aggregate/Deal.md §«Писатели четвёрки и их
     * триггеры»). Той же транзакцией замораживается база риска сделки:
     * снимок write-once, и первым его ставит ПЕРВЫЙ сайзинг, каким бы
     * траншем он ни делался.
     *
     * <p><b>Пересчёт запрещён на неполном графе</b>: числа считаются по
     * ногам, защитам и эпизодам из контекста прохода, и на неполном
     * графе {@code plannedRiskAmount} вышел бы ЗАНИЖЕННЫМ — то есть
     * ослабил бы кумулятивный потолок. Прежние значения тогда остаются
     * нетронутыми.
     */
    private void applyRiskNumbers(CreateOrderCommandPayload payload, Order saved, DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        freezeRiskBase(payload, deal, dealContext);
        if (isFalse(DealRiskNumbers.recomputeAllowed(dealContext.getGraphComplete()))) {
            return;
        }
        appendToGraph(deal, saved);
        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);
        deal.setPlannedRiskAmount(numbers.getPlannedRiskAmount());
        deal.setIncurredRiskAmount(numbers.getIncurredRiskAmount());
        deal.setCurrentRiskAmount(numbers.getCurrentRiskAmount());
        deal.setProtectionRelievedRiskAmount(numbers.getProtectionRelievedRiskAmount());
        dealDataService.save(deal);
    }

    /**
     * База риска и валюта риска сделки — write-once снимки момента
     * ПЕРВОГО сайзинга, а не производные графа: они пишутся одним ходом
     * при заведении ноги и пересчёту не подлежат. Значением снимок и
     * живая база совпадают ровно в момент заморозки; расходятся они
     * потом, и с этого момента потолки живой сделки считаются от снимка —
     * заморозка внутри сделки и есть смысл поля.
     */
    private void freezeRiskBase(CreateOrderCommandPayload payload, Deal deal, DealContext dealContext) {
        boolean changed = false;
        if (isNull(deal.getPlannedRiskEquityBase()) && nonNull(dealContext.getExchange())) {
            deal.setPlannedRiskEquityBase(dealContext.getExchange().getRiskBase());
            changed = nonNull(deal.getPlannedRiskEquityBase());
        }
        if (isNull(deal.getPlannedRiskCurrency()) && nonNull(payload.getPlannedRiskCurrency())) {
            deal.setPlannedRiskCurrency(payload.getPlannedRiskCurrency());
            changed = true;
        }
        if (changed) {
            dealDataService.save(deal);
        }
    }

    /**
     * Свежесозданная нога входит в граф прохода ТОЙ ЖЕ транзакцией:
     * контекст собран до неё, и без этого числа считались бы по графу
     * без только что заведённой ноги — ровно на ту величину заниженными.
     */
    private void appendToGraph(Deal deal, Order saved) {
        List<Order> orders = new ArrayList<>(emptyIfNull(deal.getOrders()));
        if (orders.stream().noneMatch(order -> Objects.equals(saved.getId(), order.getId()))) {
            orders.add(saved);
            deal.setOrders(orders);
        }
        deal.getTranches().stream()
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
        order.setInternalId(ClientIdGenerator.generate());
        order.setStatus(Order.Status.CREATED);
        order.setType(payload.getOrderType());
        order.setSide(payload.getSide());
        order.setSize(payload.getSizeContracts());
        order.setPositionReducingOnly(payload.getPositionReducingOnly());
        if (isTrue(payload.getSendPriceToExchange())) {
            order.setPrice(payload.getPrice());
        }
        order.setDealTrancheId(payload.getDealTrancheId());
        order.setAttachedAlgoOrders(buildAttached(payload.getAttachedProtection()));
        // Шесть чисел планового риска ноги — write-once снимок момента
        // постановки: «под какой риск сайзились» отвечает тогдашним
        // состоянием, а не сегодняшним, поэтому они не пересчитываются.
        order.setPlannedEntryPrice(payload.getPlannedEntryPrice());
        order.setPlannedSizeContracts(payload.getSizeContracts());
        order.setPlannedRiskAmount(payload.getPlannedRiskAmount());
        order.setPlannedRiskCurrency(payload.getPlannedRiskCurrency());
        order.setPlannedContractValue(payload.getPlannedContractValue());
        order.setPlannedStopPrice(payload.getPlannedStopPrice());
        return order;
    }

    private List<AttachedAlgoOrder> buildAttached(AttachedProtectionPayload protection) {
        if (isNull(protection)) {
            return null;
        }
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setInternalId(ClientIdGenerator.generate());
        attached.setStatus(AttachedAlgoOrder.Status.CREATED);
        attached.setType(protection.getAttachedType());
        attached.setStopLossTriggerPrice(protection.getStopLossTriggerPrice());
        attached.setTriggerPriceType(protection.getTriggerPriceType());
        attached.setSize(protection.getSize());
        return List.of(attached);
    }
}
