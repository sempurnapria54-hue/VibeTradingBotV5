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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshAttachedAlgoOrderExecutor {

    private final OrderDataService orderDataService;

    @Transactional
    public void refreshAttachedAlgoOrders(Order order, OrderExternalSnapshot snapshot) {
        List<AttachedAlgoOrder> localChildren = ensureChildren(order);
        List<AttachedAlgoOrderExternalSnapshot> externalChildren = extractExternalChildren(snapshot);

        for (AttachedAlgoOrderExternalSnapshot external : externalChildren) {
            AttachedAlgoOrder child = findMatch(localChildren, external)
                    .orElseGet(() -> createChild(order, localChildren, external));

            boolean changed = updateFromExternal(child, external);
            boolean statusChanged = applyStatusFromExternalProof(child, external);
            if (changed || statusChanged) {
                persist(order);
            }
        }
    }

    @Transactional
    public void execute(Order order, OrderExternalSnapshot snapshot) {
        refreshAttachedAlgoOrders(order, snapshot);
    }

    private List<AttachedAlgoOrderExternalSnapshot> extractExternalChildren(OrderExternalSnapshot snapshot) {
        if (snapshot.getAttachedAlgoOrders() == null) {
            return List.of();
        }
        return snapshot.getAttachedAlgoOrders();
    }

    private List<AttachedAlgoOrder> ensureChildren(Order order) {
        if (order.getAttachedAlgoOrders() == null) {
            order.setAttachedAlgoOrders(new ArrayList<>());
        }
        return order.getAttachedAlgoOrders();
    }

    private Optional<AttachedAlgoOrder> findMatch(List<AttachedAlgoOrder> localChildren,
                                                  AttachedAlgoOrderExternalSnapshot external) {
        return localChildren.stream()
                            .filter(local -> Objects.equals(local.getExternalAttachedId(),
                                                            external.getExternalAttachedId())
                                    && local.getExternalAttachedId() != null)
                            .findFirst()
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.equals(local.getExternalId(),
                                                                                   external.getExternalId())
                                                           && local.getExternalId() != null)
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.equals(local.getInternalId(),
                                                                                   external.getInternalId())
                                                           && local.getInternalId() != null)
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> local.getType()
                                                           == AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS)
                                                   .max(Comparator.comparing(AttachedAlgoOrder::getCreatedAt,
                                                                             Comparator.nullsLast(
                                                                                     Comparator.naturalOrder()))));
    }

    private AttachedAlgoOrder createChild(Order order,
                                          List<AttachedAlgoOrder> localChildren,
                                          AttachedAlgoOrderExternalSnapshot external) {
        AttachedAlgoOrder created = new AttachedAlgoOrder();
        created.setOrderId(order.getId());
        created.setInternalId(isNotBlank(external.getInternalId()) ? external.getInternalId() : UUID.randomUUID()
                .toString());
        created.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
        created.setStatus(AttachedAlgoOrder.Status.CREATED);
        localChildren.add(created);
        return created;
    }

    private boolean updateFromExternal(AttachedAlgoOrder local, AttachedAlgoOrderExternalSnapshot external) {
        boolean changed = false;
        changed |= setIfChanged(local.getInternalId(), external.getInternalId(), local::setInternalId);
        changed |= setIfChanged(local.getExternalAttachedId(), external.getExternalAttachedId(),
                                local::setExternalAttachedId);
        changed |= setIfChanged(local.getExternalId(), external.getExternalId(), local::setExternalId);
        changed |= setIfChanged(local.getExternalType(), external.getExternalType(), local::setExternalType);

        BigDecimal newSize = parseDecimal(external.getSize());
        if (!Objects.equals(local.getSize(), newSize) && newSize != null) {
            local.setSize(newSize);
            changed = true;
        }

        BigDecimal newSlTrigger = parseDecimal(external.getStopLossTriggerPrice());
        if (!Objects.equals(local.getStopLossTriggerPrice(), newSlTrigger) && newSlTrigger != null) {
            local.setStopLossTriggerPrice(newSlTrigger);
            changed = true;
        }

        if (local.getType() != AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS) {
            local.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
            changed = true;
        }

        return changed;
    }

    private boolean applyStatusFromExternalProof(AttachedAlgoOrder local,
                                                AttachedAlgoOrderExternalSnapshot external) {
        AttachedAlgoOrder.Status before = local.getStatus();
        local.toAttached();
        return before != local.getStatus();
    }

    private boolean setIfChanged(String current, String candidate, java.util.function.Consumer<String> setter) {
        if (!isNotBlank(candidate) || Objects.equals(current, candidate)) {
            return false;
        }
        setter.accept(candidate);
        return true;
    }

    private void persist(Order order) {
        orderDataService.save(order);
    }

    private BigDecimal parseDecimal(String value) {
        if (!isNotBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
