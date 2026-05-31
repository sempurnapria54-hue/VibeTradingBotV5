package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class AlgoOrderSyncService {

    private final AlgoOrderMapper algoOrderMapper;

    public boolean applyLiveSnapshot(AlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(target) || Objects.isNull(snapshot)) {
            return false;
        }

        AlgoOrder.Status previousStatus = target.getStatus();
        algoOrderMapper.updateDomainFromExternalSnapshot(snapshot, target);

        if (isLive(snapshot.getExternalStatus())) {
            target.setStatus(AlgoOrder.Status.ACTIVE);
        }

        return isFalse(Objects.equals(previousStatus, target.getStatus()));
    }

    public boolean applySnapshot(AlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(target) || Objects.isNull(snapshot)) {
            return false;
        }

        AlgoOrder.Status previousStatus = target.getStatus();
        algoOrderMapper.updateDomainFromExternalSnapshot(snapshot, target);

        AlgoOrder.Status resolved = resolveAlgoStatus(snapshot);
        if (Objects.nonNull(resolved)) {
            target.setStatus(resolved);
        }

        return isFalse(Objects.equals(previousStatus, target.getStatus()));
    }

    public void applyLiveSnapshot(AttachedAlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(target) || Objects.isNull(snapshot)) {
            return;
        }

        updateAttachedIdentity(target, snapshot);
        updateAttachedFields(target, snapshot);

        if (isLive(snapshot.getExternalStatus())
                && target.canTransitionTo(AttachedAlgoOrder.Status.ACTIVE)) {
            target.toActive();
        }
    }

    public void applyFinalSnapshot(AttachedAlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(target) || Objects.isNull(snapshot)) {
            return;
        }

        applyLiveSnapshot(target, snapshot);

        String normalizedStatus = normalize(snapshot.getExternalStatus());
        if ((Objects.equals("effective", normalizedStatus) || Objects.equals("canceled", normalizedStatus))
                && target.canTransitionTo(AttachedAlgoOrder.Status.CLOSED)) {
            target.toClose();
            return;
        }

        if ((Objects.equals("order_failed", normalizedStatus)
                || Objects.equals("failed", normalizedStatus)
                || hasFailureProof(snapshot))
                && target.canTransitionTo(AttachedAlgoOrder.Status.FAILED)) {
            target.toFail();
        }
    }

    private AlgoOrder.Status resolveAlgoStatus(AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(snapshot)) {
            return null;
        }

        String normalizedStatus = normalize(snapshot.getExternalStatus());
        if (Objects.isNull(normalizedStatus)) {
            if (hasFailureProof(snapshot)) {
                return AlgoOrder.Status.FAILED;
            }
            return null;
        }

        return switch (normalizedStatus) {
            case "live", "pause" -> AlgoOrder.Status.ACTIVE;
            case "effective", "canceled" -> AlgoOrder.Status.CLOSED;
            case "order_failed", "failed" -> AlgoOrder.Status.FAILED;
            default -> {
                if (hasFailureProof(snapshot)) {
                    yield AlgoOrder.Status.FAILED;
                }
                yield null;
            }
        };
    }

    private void updateAttachedIdentity(AttachedAlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (isNotBlank(snapshot.getExternalId())
                && isFalse(Objects.equals(target.getExternalId(), snapshot.getExternalId()))) {
            target.setExternalId(snapshot.getExternalId());
        }
        if (isNotBlank(snapshot.getInternalId())
                && isFalse(Objects.equals(target.getInternalId(), snapshot.getInternalId()))) {
            target.setInternalId(snapshot.getInternalId());
        }
        if (isNotBlank(snapshot.getExternalType())
                && isFalse(Objects.equals(target.getExternalType(), snapshot.getExternalType()))) {
            target.setExternalType(snapshot.getExternalType());
        }
    }

    private void updateAttachedFields(AttachedAlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (isFalse(Objects.equals(target.getExternalStatus(), snapshot.getExternalStatus()))) {
            target.setExternalStatus(snapshot.getExternalStatus());
        }
        if (Objects.nonNull(snapshot.getSize())
                && isFalse(Objects.equals(target.getSize(), snapshot.getSize()))) {
            target.setSize(snapshot.getSize());
        }

        BigDecimal stopLossTriggerPrice = resolveStopLossTriggerPrice(snapshot);
        if (Objects.nonNull(stopLossTriggerPrice)
                && isFalse(Objects.equals(target.getStopLossTriggerPrice(), stopLossTriggerPrice))) {
            target.setStopLossTriggerPrice(stopLossTriggerPrice);
        }
    }

    private BigDecimal resolveStopLossTriggerPrice(AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(snapshot.getCondition())) {
            return null;
        }
        if (Objects.isNull(snapshot.getCondition()
                                  .getTrigger())) {
            return null;
        }
        if (Objects.isNull(snapshot.getCondition()
                                  .getTrigger()
                                  .getStopLoss())) {
            return null;
        }

        return snapshot.getCondition()
                       .getTrigger()
                       .getStopLoss()
                       .getExternalValue();
    }

    private boolean hasFailureProof(AlgoOrderExternalSnapshot snapshot) {
        return isNotBlank(snapshot.getFailCode());
    }

    private boolean isLive(String externalStatus) {
        String normalizedStatus = normalize(externalStatus);
        return Objects.equals("live", normalizedStatus) || Objects.equals("pause", normalizedStatus);
    }

    private String normalize(String status) {
        if (isFalse(isNotBlank(status))) {
            return null;
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private boolean isNotBlank(String value) {
        return Objects.nonNull(value) && isFalse(value.isBlank());
    }
}
