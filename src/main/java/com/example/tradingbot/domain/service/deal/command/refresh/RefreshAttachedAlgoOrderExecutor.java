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
            transitionToAttachedIfAllowed(order, local);
            touched.add(local);
        }

        if (shouldFailMissingAttached(snapshot)) {
            localChildren.stream()
                         .filter(local -> !touched.contains(local))
                         .forEach(local -> transitionToFailedIfAllowed(order, local));
        }

        if (shouldCloseMissingAttached(snapshot)) {
            localChildren.stream()
                         .filter(this::isLive)
                         .filter(local -> !touched.contains(local))
                         .forEach(local -> transitionToClosedIfAllowed(order, local));
        }
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

    private void transitionToAttachedIfAllowed(Order order, AttachedAlgoOrder local) {
        AttachedAlgoOrder.Status before = local.getStatus();
        local.toAttached();
        persistOnStatusChange(order, local, before);
    }

    private void transitionToClosedIfAllowed(Order order, AttachedAlgoOrder local) {
        AttachedAlgoOrder.Status before = local.getStatus();
        local.toClose();
        persistOnStatusChange(order, local, before);
    }

    private void transitionToFailedIfAllowed(Order order, AttachedAlgoOrder local) {
        AttachedAlgoOrder.Status before = local.getStatus();
        local.toFail();
        persistOnStatusChange(order, local, before);
    }

    private void persistOnStatusChange(Order order, AttachedAlgoOrder local, AttachedAlgoOrder.Status beforeStatus) {
        if (beforeStatus != local.getStatus()) {
            orderDataService.save(order);
        }
    }

    private boolean shouldCloseMissingAttached(OrderExternalSnapshot snapshot) {
        if (snapshot.getAttachedAlgoOrders() != null && !snapshot.getAttachedAlgoOrders().isEmpty()) {
            return false;
        }
        if (snapshot.getExternalStatus() == null) {
            return false;
        }
        String normalized = snapshot.getExternalStatus().toLowerCase();
        return "canceled".equals(normalized) || "mmp_canceled".equals(normalized);
    }

    private boolean shouldFailMissingAttached(OrderExternalSnapshot snapshot) {
        if (snapshot.getAttachedAlgoOrders() != null && !snapshot.getAttachedAlgoOrders().isEmpty()) {
            return false;
        }
        if (snapshot.getExternalStatus() == null) {
            return false;
        }
        String normalized = snapshot.getExternalStatus().toLowerCase();
        return "failed".equals(normalized)
                || "order_failed".equals(normalized)
                || "rejected".equals(normalized);
    }

    private boolean isLive(AttachedAlgoOrder order) {
        return !order.isTerminal();
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
