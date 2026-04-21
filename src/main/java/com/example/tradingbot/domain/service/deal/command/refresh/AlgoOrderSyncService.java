package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        return !Objects.equals(previousStatus, target.getStatus());
    }

    public boolean applySnapshot(AlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(target) || Objects.isNull(snapshot)) {
            return false;
        }

        AlgoOrder.Status previousStatus = target.getStatus();
        algoOrderMapper.updateDomainFromExternalSnapshot(snapshot, target);
        AlgoOrder.Status resolved = resolveAlgoStatus(snapshot.getExternalStatus());
        if (Objects.nonNull(resolved)) {
            target.setStatus(resolved);
        }
        return !Objects.equals(previousStatus, target.getStatus());
    }

    public void applyLiveSnapshot(AttachedAlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (Objects.isNull(target) || Objects.isNull(snapshot)) {
            return;
        }

        if (isBlank(target.getExternalId()) && isNotBlank(snapshot.getExternalId())) {
            target.setExternalId(snapshot.getExternalId());
        }
        if (isBlank(target.getInternalId()) && isNotBlank(snapshot.getInternalId())) {
            target.setInternalId(snapshot.getInternalId());
        }
        if (isBlank(target.getExternalType()) && isNotBlank(snapshot.getExternalType())) {
            target.setExternalType(snapshot.getExternalType());
        }
        target.setExternalStatus(snapshot.getExternalStatus());
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

        String status = normalize(snapshot.getExternalStatus());
        if ((Objects.equals("effective", status) || Objects.equals("canceled", status))
                && target.canTransitionTo(AttachedAlgoOrder.Status.CLOSED)) {
            target.toClose();
            return;
        }
        if ((Objects.equals("order_failed", status) || Objects.equals("failed", status))
                && target.canTransitionTo(AttachedAlgoOrder.Status.FAILED)) {
            target.toFail();
        }
    }

    private AlgoOrder.Status resolveAlgoStatus(String externalStatus) {
        String normalized = normalize(externalStatus);
        if (Objects.isNull(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "live", "pause" -> AlgoOrder.Status.ACTIVE;
            case "effective", "canceled" -> AlgoOrder.Status.CLOSED;
            case "order_failed", "failed" -> AlgoOrder.Status.FAILED;
            default -> null;
        };
    }

    private boolean isLive(String externalStatus) {
        String normalized = normalize(externalStatus);
        return Objects.equals("live", normalized) || Objects.equals("pause", normalized);
    }

    private String normalize(String status) {
        if (isBlank(status)) {
            return null;
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }

    private boolean isNotBlank(String value) {
        return Objects.nonNull(value) && isFalse(value.isBlank());
    }
}
