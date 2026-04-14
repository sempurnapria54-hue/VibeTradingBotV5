package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshAttachedAlgoOrderExecutor {

    private final OrderDataService orderDataService;

    @Transactional
    public void execute(Order order, OrderExternalSnapshot snapshot) {
        List<AttachedAlgoOrder> localChildren = ensureChildren(order);
        List<AttachedAlgoOrderExternalSnapshot> externalChildren =
                snapshot.getAttachedAlgoOrders() == null ? List.of() : snapshot.getAttachedAlgoOrders();

        List<AttachedAlgoOrder> touched = new ArrayList<>();
        for (AttachedAlgoOrderExternalSnapshot external : externalChildren) {
            AttachedAlgoOrder local = findMatch(localChildren, external).orElseGet(() -> {
                AttachedAlgoOrder created = new AttachedAlgoOrder();
                created.setOrderId(order.getId());
                created.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
                localChildren.add(created);
                return created;
            });
            updateFromExternal(local, external);
            resolveAttachedStatus(local);
            touched.add(local);
        }

        if (shouldCloseMissingAttached(order, snapshot)) {
            localChildren.stream()
                         .filter(this::isLive)
                         .filter(local -> !touched.contains(local))
                         .forEach(local -> local.setStatus(AttachedAlgoOrder.Status.CLOSED));
        }

        orderDataService.save(order);
    }

    private List<AttachedAlgoOrder> ensureChildren(Order order) {
        if (order.getAttachedAlgoOrders() == null) {
            order.setAttachedAlgoOrders(new ArrayList<>());
        }
        return order.getAttachedAlgoOrders();
    }

    private Optional<AttachedAlgoOrder> findMatch(List<AttachedAlgoOrder> localChildren,
                                                  AttachedAlgoOrderExternalSnapshot external) {
        if (external == null) {
            return Optional.empty();
        }

        return localChildren.stream()
                            .filter(local -> Objects.equals(local.getExternalAttachedId(),
                                                            external.getExternalAttachedId()))
                            .findFirst()
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.equals(local.getExternalId(),
                                                                                   external.getExternalId()))
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.equals(local.getInternalId(),
                                                                                   external.getInternalId()))
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> local.getType()
                                                           == AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS)
                                                   .max(Comparator.comparing(AttachedAlgoOrder::getCreatedAt,
                                                                             Comparator.nullsLast(
                                                                                     Comparator.naturalOrder()))));
    }

    private void updateFromExternal(AttachedAlgoOrder local, AttachedAlgoOrderExternalSnapshot external) {
        local.setInternalId(orCurrent(local.getInternalId(), external.getInternalId()));
        local.setExternalAttachedId(orCurrent(local.getExternalAttachedId(), external.getExternalAttachedId()));
        local.setExternalId(orCurrent(local.getExternalId(), external.getExternalId()));
        local.setExternalType(orCurrent(local.getExternalType(), external.getExternalType()));
        local.setSize(parseDecimal(orCurrent(local.getSize() == null ? null : local.getSize().toPlainString(),
                                             external.getSize())));
        local.setStopLossTriggerPrice(parseDecimal(orCurrent(local.getStopLossTriggerPrice() == null ? null :
                                                                     local.getStopLossTriggerPrice().toPlainString(),
                                                             external.getStopLossTriggerPrice())));
        local.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
    }

    private void resolveAttachedStatus(AttachedAlgoOrder local) {
        if (local.getStatus() == AttachedAlgoOrder.Status.ACTIVE) {
            return;
        }
        local.setStatus(AttachedAlgoOrder.Status.ATTACHED);
    }

    private boolean shouldCloseMissingAttached(Order order, OrderExternalSnapshot snapshot) {
        if (snapshot.getAttachedAlgoOrders() != null && !snapshot.getAttachedAlgoOrders().isEmpty()) {
            return false;
        }
        if (snapshot.getExternalStatus() == null) {
            return false;
        }
        return "canceled".equalsIgnoreCase(snapshot.getExternalStatus());
    }

    private boolean isLive(AttachedAlgoOrder order) {
        return order.getStatus() != AttachedAlgoOrder.Status.CLOSED
                && order.getStatus() != AttachedAlgoOrder.Status.FAILED;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String orCurrent(String current, String candidate) {
        return (candidate == null || candidate.isBlank()) ? current : candidate;
    }
}
