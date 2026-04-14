package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AlgoOrderSyncService {

    private final AlgoOrderMapper algoOrderMapper;

    public boolean applyLiveSnapshot(AlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (target == null || snapshot == null) {
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
        if (target == null || snapshot == null) {
            return false;
        }

        AlgoOrder.Status previousStatus = target.getStatus();
        algoOrderMapper.updateDomainFromExternalSnapshot(snapshot, target);
        AlgoOrder.Status resolved = resolveAlgoStatus(snapshot.getExternalStatus());
        if (resolved != null) {
            target.setStatus(resolved);
        }
        return !Objects.equals(previousStatus, target.getStatus());
    }

    public void applyLiveSnapshot(AttachedAlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (target == null || snapshot == null) {
            return;
        }

        if (isBlank(target.getExternalId()) && !isBlank(snapshot.getExternalId())) {
            target.setExternalId(snapshot.getExternalId());
        }
        if (isBlank(target.getInternalId()) && !isBlank(snapshot.getInternalId())) {
            target.setInternalId(snapshot.getInternalId());
        }
        if (isBlank(target.getExternalType()) && !isBlank(snapshot.getExternalType())) {
            target.setExternalType(snapshot.getExternalType());
        }
        target.setExternalStatus(snapshot.getExternalStatus());
        if (isLive(snapshot.getExternalStatus())) {
            target.toActive();
        }
    }

    public void applyFinalSnapshot(AttachedAlgoOrder target, AlgoOrderExternalSnapshot snapshot) {
        if (target == null || snapshot == null) {
            return;
        }
        applyLiveSnapshot(target, snapshot);

        String status = normalize(snapshot.getExternalStatus());
        if ("effective".equals(status) || "canceled".equals(status)) {
            target.toClose();
            return;
        }
        if ("order_failed".equals(status) || "failed".equals(status)) {
            target.toFail();
        }
    }

    private AlgoOrder.Status resolveAlgoStatus(String externalStatus) {
        String normalized = normalize(externalStatus);
        if (normalized == null) {
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
        return "live".equals(normalized) || "pause".equals(normalized);
    }

    private String normalize(String status) {
        if (isBlank(status)) {
            return null;
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
