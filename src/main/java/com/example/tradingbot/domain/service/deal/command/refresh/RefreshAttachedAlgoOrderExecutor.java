package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.persistence.service.AttachedAlgoOrderDataService;
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
import java.util.function.Consumer;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class RefreshAttachedAlgoOrderExecutor {

    private static final String TOP_LEVEL_ATTACHED_EXTERNAL_TYPE = "attachAlgoOrds";

    private final AttachedAlgoOrderDataService attachedAlgoOrderDataService;

    @Transactional
    public void refreshAttachedAlgoOrders(Order order, OrderExternalSnapshot snapshot) {
        validateOrder(order);

        List<AttachedAlgoOrder> localChildren = new ArrayList<>(attachedAlgoOrderDataService.findAllByOrderId(
                order.getId()));
        List<AttachedAlgoOrderExternalSnapshot> externalChildren = extractExternalChildren(order, snapshot);
        Set<AttachedAlgoOrder> matchedLocalChildren = new HashSet<>();

        for (AttachedAlgoOrderExternalSnapshot external : externalChildren) {
            AttachedAlgoOrder child = findMatch(localChildren, external)
                    .orElseGet(() -> createChild(order, localChildren, external));
            matchedLocalChildren.add(child);

            boolean changed = updateFromExternal(order, child, external);
            changed |= applyStatusFromExternalProof(child, external);

            if (changed) {
                persist(child);
            }
        }

        applyMissingLocalChildrenLifecycle(order, localChildren, matchedLocalChildren);
    }

    @Transactional
    public void execute(Order order, OrderExternalSnapshot snapshot) {
        refreshAttachedAlgoOrders(order, snapshot);
    }

    private void validateOrder(Order order) {
        if (Objects.isNull(order)) {
            throw new IllegalStateException("Order is required for attached child refresh");
        }
        if (Objects.isNull(order.getId())) {
            throw new IllegalStateException("Order must be persisted before attached child refresh");
        }
    }

    private List<AttachedAlgoOrderExternalSnapshot> extractExternalChildren(Order order, OrderExternalSnapshot snapshot) {
        if (Objects.isNull(snapshot)) {
            return List.of();
        }
        if (CollectionUtils.isEmpty(snapshot.getAttachedAlgoOrders())) {
            AttachedAlgoOrderExternalSnapshot topLevelChild = buildTopLevelChildSnapshot(order, snapshot);
            if (Objects.isNull(topLevelChild)) {
                return List.of();
            }
            return List.of(topLevelChild);
        }

        return mergeTopLevelProof(order, snapshot);
    }

    private AttachedAlgoOrderExternalSnapshot buildTopLevelChildSnapshot(Order order, OrderExternalSnapshot snapshot) {
        if (isFalse(hasTopLevelAttachedProof(snapshot))) {
            return null;
        }
        if (Objects.isNull(order.getSize())) {
            return null;
        }

        AttachedAlgoOrderExternalSnapshot external = new AttachedAlgoOrderExternalSnapshot();
        external.setInternalId(snapshot.getAttachedAlgoInternalId());
        external.setExternalType(TOP_LEVEL_ATTACHED_EXTERNAL_TYPE);

        if (Objects.nonNull(snapshot.getStopLossTriggerPrice())) {
            external.setStopLossTriggerPrice(snapshot.getStopLossTriggerPrice()
                                                     .toPlainString());
        }
        if (Objects.nonNull(order.getSize())) {
            external.setSize(order.getSize()
                                  .toPlainString());
        }

        return external;
    }

    private boolean hasTopLevelAttachedProof(OrderExternalSnapshot snapshot) {
        return Objects.nonNull(snapshot.getStopLossTriggerPrice());
    }

    private List<AttachedAlgoOrderExternalSnapshot> mergeTopLevelProof(Order order, OrderExternalSnapshot snapshot) {
        List<AttachedAlgoOrderExternalSnapshot> merged = new ArrayList<>(snapshot.getAttachedAlgoOrders());
        if (merged.size() != 1) {
            return merged;
        }

        AttachedAlgoOrderExternalSnapshot external = merged.getFirst();
        if (isFalse(isNotBlank(external.getInternalId())) && isNotBlank(snapshot.getAttachedAlgoInternalId())) {
            external.setInternalId(snapshot.getAttachedAlgoInternalId());
        }
        if (isFalse(isNotBlank(external.getStopLossTriggerPrice()))
                && Objects.nonNull(snapshot.getStopLossTriggerPrice())) {
            external.setStopLossTriggerPrice(snapshot.getStopLossTriggerPrice()
                                                     .toPlainString());
        }
        if (isFalse(isNotBlank(external.getSize())) && Objects.nonNull(order.getSize())) {
            external.setSize(order.getSize()
                                  .toPlainString());
        }

        return merged;
    }

    private Optional<AttachedAlgoOrder> findMatch(List<AttachedAlgoOrder> localChildren,
                                                  AttachedAlgoOrderExternalSnapshot external) {
        return localChildren.stream()
                            .filter(local -> Objects.nonNull(local.getExternalAttachedId()))
                            .filter(local -> Objects.equals(local.getExternalAttachedId(),
                                                            external.getExternalAttachedId()))
                            .findFirst()
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.nonNull(local.getExternalId()))
                                                   .filter(local -> Objects.equals(local.getExternalId(),
                                                                                   external.getExternalId()))
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.nonNull(local.getInternalId()))
                                                   .filter(local -> Objects.equals(local.getInternalId(),
                                                                                   external.getInternalId()))
                                                   .findFirst())
                            .or(() -> localChildren.stream()
                                                   .filter(local -> Objects.equals(local.getType(),
                                                                                   AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS))
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
        if (Objects.nonNull(order.getSize())) {
            created.setSize(order.getSize());
        }
        localChildren.add(created);
        return created;
    }

    private boolean updateFromExternal(Order order,
                                       AttachedAlgoOrder local,
                                       AttachedAlgoOrderExternalSnapshot external) {
        boolean changed = false;
        changed |= setIfChanged(local.getInternalId(), external.getInternalId(), local::setInternalId);
        changed |= setIfChanged(local.getExternalAttachedId(),
                                external.getExternalAttachedId(),
                                local::setExternalAttachedId);
        changed |= setIfChanged(local.getExternalId(), external.getExternalId(), local::setExternalId);
        changed |= setIfChanged(local.getExternalType(), external.getExternalType(), local::setExternalType);

        BigDecimal newSize = parseDecimal(external.getSize());
        if (Objects.nonNull(newSize) && isFalse(Objects.equals(local.getSize(), newSize))) {
            local.setSize(newSize);
            changed = true;
        }
        if (Objects.isNull(newSize)
                && Objects.nonNull(order.getSize())
                && Objects.isNull(local.getSize())) {
            local.setSize(order.getSize());
            changed = true;
        }

        BigDecimal newStopLossTrigger = parseDecimal(external.getStopLossTriggerPrice());
        if (Objects.nonNull(newStopLossTrigger)
                && isFalse(Objects.equals(local.getStopLossTriggerPrice(), newStopLossTrigger))) {
            local.setStopLossTriggerPrice(newStopLossTrigger);
            changed = true;
        }

        if (isFalse(Objects.equals(local.getType(), AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS))) {
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

    private void applyMissingLocalChildrenLifecycle(Order order,
                                                    List<AttachedAlgoOrder> localChildren,
                                                    Set<AttachedAlgoOrder> matchedLocalChildren) {
        for (AttachedAlgoOrder localChild : localChildren) {
            if (matchedLocalChildren.contains(localChild)) {
                continue;
            }
            if (localChild.isTerminal()) {
                continue;
            }
            if (isOrderSnapshotCloseTransitionAllowed(order, localChild)) {
                localChild.toClose();
                persist(localChild);
            }
        }
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

    private boolean setIfChanged(String current, String candidate, Consumer<String> setter) {
        if (isFalse(isNotBlank(candidate)) || Objects.equals(current, candidate)) {
            return false;
        }
        setter.accept(candidate);
        return true;
    }

    private void persist(AttachedAlgoOrder child) {
        AttachedAlgoOrder saved = attachedAlgoOrderDataService.save(child);
        child.setId(saved.getId());
        child.setCreatedAt(saved.getCreatedAt());
        child.setCreatedBy(saved.getCreatedBy());
        child.setModifiedAt(saved.getModifiedAt());
        child.setModifiedBy(saved.getModifiedBy());
        child.setExternalCreatedAt(saved.getExternalCreatedAt());
        child.setExternalModifiedAt(saved.getExternalModifiedAt());
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
