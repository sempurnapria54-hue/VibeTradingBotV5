package com.example.tradingbot.domain.command.executor;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.model.core.fill.external_snapshot.FillExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет REFRESH_FILLS: грузит fills с биржи с эскалацией 3d→3m
 * внутри команды (getFills + getFillsHistory, merge по tradeId),
 * сопоставляет с ordinary orders сделки по ordId и идемпотентно
 * пересчитывает accumulatedFillSize / averagePrice / fee (recompute из
 * fills — повтор не задваивает). TradeFill как entity не вводится.
 * (Пагинация назад по billId — refinement; Deal.resultProfit считает
 * FSM по фактам.) См. docs/components/RefreshFillsExecutor.md,
 * docs/decisions/refresh-evidence-cycle-ownership.md.
 */
@Component
@RequiredArgsConstructor
public class RefreshFillsExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_FILLS;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        String instId = dealContext.getInstrument().getExternalId();
        List<FillExternalSnapshot> fills = mergeByTradeId(
                integrationService.getFills(instId, null, null),
                integrationService.getFillsHistory(instId, null, null));
        if (isNotEmpty(fills) && isNotEmpty(dealContext.getDeal().getOrders())) {
            Map<String, List<FillExternalSnapshot>> byOrderId = fills.stream()
                    .filter(fill -> isNotBlank(fill.getExternalOrderId()))
                    .collect(groupingBy(FillExternalSnapshot::getExternalOrderId));
            dealContext.getDeal().getOrders().forEach(order -> applyFills(order, byOrderId));
        }
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    private void applyFills(Order order, Map<String, List<FillExternalSnapshot>> byOrderId) {
        List<FillExternalSnapshot> orderFills = byOrderId.getOrDefault(order.getExternalId(), emptyList());
        if (isEmpty(orderFills)) {
            return;
        }
        BigDecimal totalSize = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal weightedPrice = BigDecimal.ZERO;
        for (FillExternalSnapshot fill : orderFills) {
            BigDecimal size = value(fill.getFillSize());
            totalSize = totalSize.add(size);
            totalFee = totalFee.add(value(fill.getFee()));
            weightedPrice = weightedPrice.add(value(fill.getFillPrice()).multiply(size));
        }
        order.setAccumulatedFillSize(totalSize);
        order.setFee(totalFee);
        if (totalSize.compareTo(BigDecimal.ZERO) > 0) {
            order.setAveragePrice(weightedPrice.divide(totalSize, Constants.Calc.MATH_CONTEXT));
        }
        orderDataService.save(order);
    }

    /** Merge fills 3d + 3m по tradeId (уникально, recent имеет приоритет). */
    private List<FillExternalSnapshot> mergeByTradeId(List<FillExternalSnapshot> recent,
                                                      List<FillExternalSnapshot> history) {
        Map<String, FillExternalSnapshot> unique = new LinkedHashMap<>();
        addUnique(unique, recent);
        addUnique(unique, history);
        return new ArrayList<>(unique.values());
    }

    private void addUnique(Map<String, FillExternalSnapshot> target, List<FillExternalSnapshot> fills) {
        if (isEmpty(fills)) {
            return;
        }
        fills.forEach(fill -> {
            if (isNotBlank(fill.getExternalTradeId())) {
                target.putIfAbsent(fill.getExternalTradeId(), fill);
            }
        });
    }

    private BigDecimal value(BigDecimal candidate) {
        return isNull(candidate) ? BigDecimal.ZERO : candidate;
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
