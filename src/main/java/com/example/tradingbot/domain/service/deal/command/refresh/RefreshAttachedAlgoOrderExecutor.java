package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class RefreshAttachedAlgoOrderExecutor {

    private final OrderDataService orderDataService;

    @Transactional
    public void refreshAttachedAlgoOrders(Order order, OrderExternalSnapshot snapshot) {
        List<AttachedAlgoOrder> localChildren = ensureChildren(order);
        List<AttachedAlgoOrderExternalSnapshot> externalChildren = extractExternalChildren(snapshot);
        Set<AttachedAlgoOrder> matchedLocalChildren = new HashSet<>();
        boolean changed = false;

        for (AttachedAlgoOrderExternalSnapshot external : externalChildren) {
            AttachedAlgoOrder child = findMatch(localChildren, external)
                    .orElseGet(() -> createChild(order, localChildren, external));
            matchedLocalChildren.add(child);

            changed |= updateFromExternal(child, external);
            changed |= applyStatusFromExternalProof(child, external);
        }

        changed |= applyMissingLocalChildrenLifecycle(order, localChildren, matchedLocalChildren);

        if (changed) {
            persist(order);
        }
    }

    @Transactional
    public void execute(Order order, OrderExternalSnapshot snapshot) {
        refreshAttachedAlgoOrders(order, snapshot);
    }

    private List<AttachedAlgoOrderExternalSnapshot> extractExternalChildren(OrderExternalSnapshot snapshot) {
        if (Objects.isNull(snapshot) || CollectionUtils.isEmpty(snapshot.getAttachedAlgoOrders())) {
            return List.of();
        }
        return snapshot.getAttachedAlgoOrders();
    }

    private List<AttachedAlgoOrder> ensureChildren(Order order) {
        if (CollectionUtils.isEmpty(order.getAttachedAlgoOrders())) {
            order.setAttachedAlgoOrders(new ArrayList<>());
        }
        return order.getAttachedAlgoOrders();
    }

    private Optional<AttachedAlgoOrder> findMatch(List<AttachedAlgoOrder> localChildren,
                                                  AttachedAlgoOrderExternalSnapshot external) {
        return localChildren.stream()
                            .filter(local -> Objects.equals(local.getExternalAttachedId(),
                                                            external.getExternalAttachedId())
                                    && Objects.nonNull(local.getExternalAttachedId()))
                            .findFirst()
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.equals(local.getExternalId(),
                                                                                   external.getExternalId())
                                                           && Objects.nonNull(local.getExternalId()))
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.equals(local.getInternalId(),
                                                                                   external.getInternalId())
                                                           && Objects.nonNull(local.getInternalId()))
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> local.getType()
                                                           == AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS)
                                                   .filter(local -> local.canTransitionTo(
                                                           AttachedAlgoOrder.Status.ATTACHED))
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
        if (Objects.nonNull(newSize) && isFalse(Objects.equals(local.getSize(), newSize))) {
            local.setSize(newSize);
            changed = true;
        }

        BigDecimal newSlTrigger = parseDecimal(external.getStopLossTriggerPrice());
        if (Objects.nonNull(newSlTrigger)
                && isFalse(Objects.equals(local.getStopLossTriggerPrice(), newSlTrigger))) {
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
        if (hasFailureProof(external) && isOrderSnapshotFailureTransitionAllowed(local)) {
            local.toFail();
            return isFalse(Objects.equals(before, local.getStatus()));
        }

        if (local.canTransitionTo(AttachedAlgoOrder.Status.ATTACHED)) {
            local.toAttached();
        }

        return isFalse(Objects.equals(before, local.getStatus()));
    }

    private boolean applyMissingLocalChildrenLifecycle(Order order,
                                                       List<AttachedAlgoOrder> localChildren,
                                                       Set<AttachedAlgoOrder> matchedLocalChildren) {
        boolean changed = false;

        for (AttachedAlgoOrder localChild : localChildren) {
            if (matchedLocalChildren.contains(localChild)) {
                continue;
            }
            if (localChild.isTerminal()) {
                continue;
            }
            if (isOrderSnapshotCloseTransitionAllowed(order, localChild)) {
                localChild.toClose();
                changed = true;
            }
        }

        return changed;
    }

    private boolean isOrderSnapshotFailureTransitionAllowed(AttachedAlgoOrder local) {
        return Objects.equals(local.getStatus(), AttachedAlgoOrder.Status.CREATED)
                || Objects.equals(local.getStatus(), AttachedAlgoOrder.Status.ATTACHED);
    }

    private boolean isOrderSnapshotCloseTransitionAllowed(Order order, AttachedAlgoOrder localChild) {
        if (Objects.isNull(order)) {
            return false;
        }
        if (Objects.equals(localChild.getStatus(), AttachedAlgoOrder.Status.ACTIVE)) {
            return false;
        }
        if (isFalse(localChild.canTransitionTo(AttachedAlgoOrder.Status.CLOSED))) {
            return false;
        }

        return Objects.equals(order.getStatus(), Order.Status.CLOSED);
    }

    private boolean hasFailureProof(AttachedAlgoOrderExternalSnapshot external) {
        return isNotBlank(external.getFailCode()) || isNotBlank(external.getFailReason());
    }

    private boolean setIfChanged(String current, String candidate, java.util.function.Consumer<String> setter) {
        if (isFalse(isNotBlank(candidate)) || Objects.equals(current, candidate)) {
            return false;
        }
        setter.accept(candidate);
        return true;
    }

    private void persist(Order order) {
        orderDataService.save(order);
    }

    private BigDecimal parseDecimal(String value) {
        if (isFalse(isNotBlank(value))) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isNotBlank(String value) {
        return Objects.nonNull(value) && isFalse(value.isBlank());
    }
}
