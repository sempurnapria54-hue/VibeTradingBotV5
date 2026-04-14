package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefreshAlgoOrderExecutor {

    private final ClientManager clientManager;
    private final AlgoOrderDataService algoOrderDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderMapper algoOrderMapper;

    @Transactional
    public void execute(Exchange exchange, Instrument instrument) {
        refreshStandaloneAlgoOrders(exchange, instrument);
        refreshAttachedAlgoOrders(exchange, instrument);
    }

    private void refreshStandaloneAlgoOrders(Exchange exchange, Instrument instrument) {
        List<AlgoOrder> liveOrders = algoOrderDataService.findAllByInstrumentIdAndStatuses(
                instrument.getId(),
                Set.of(AlgoOrder.Status.PENDING.name(), AlgoOrder.Status.ACTIVE.name())
        );

        for (AlgoOrder algoOrder : liveOrders) {
            AlgoOrderExternalSnapshot snapshot = resolveAlgoSnapshot(exchange, instrument, algoOrder);
            if (snapshot == null) {
                continue;
            }
            algoOrderMapper.updateDomainFromExternalSnapshot(snapshot, algoOrder);
            algoOrder.setStatus(resolveAlgoStatus(snapshot.getExternalStatus()));
            algoOrderDataService.save(algoOrder);
        }
    }

    private void refreshAttachedAlgoOrders(Exchange exchange, Instrument instrument) {
        orderDataService.findByInstrumentId(instrument.getId())
                        .forEach(order -> {
                            if (order.getAttachedAlgoOrders() == null) {
                                return;
                            }
                            order.getAttachedAlgoOrders()
                                 .stream()
                                 .filter(this::requiresAlgoRefresh)
                                 .forEach(attached -> refreshAttached(exchange,
                                                                      instrument,
                                                                      order,
                                                                      attached));
                        });
    }

    private boolean requiresAlgoRefresh(AttachedAlgoOrder attached) {
        return attached.getStatus() == AttachedAlgoOrder.Status.ATTACHED
                || attached.getStatus() == AttachedAlgoOrder.Status.ACTIVE;
    }

    private void refreshAttached(Exchange exchange,
                                 Instrument instrument,
                                 com.example.tradingbot.domain.model.order.Order order,
                                 AttachedAlgoOrder attached) {
        AlgoOrder probe = new AlgoOrder();
        probe.setInternalId(attached.getInternalId());
        probe.setExternalId(attached.getExternalId());
        probe.setExternalType(attached.getExternalType());
        probe.setExternalStatus(attached.getExternalStatus());

        AlgoOrderExternalSnapshot snapshot = resolveAlgoSnapshot(exchange, instrument, probe);
        if (snapshot == null) {
            return;
        }

        attached.setExternalId(firstNonBlank(attached.getExternalId(), snapshot.getExternalId()));
        attached.setExternalType(firstNonBlank(attached.getExternalType(), snapshot.getExternalType()));
        attached.setExternalStatus(snapshot.getExternalStatus());
        attached.setStatus(resolveAttachedStatus(snapshot.getExternalStatus()));
        orderDataService.save(order);
    }

    private AlgoOrderExternalSnapshot resolveAlgoSnapshot(Exchange exchange, Instrument instrument, AlgoOrder algoOrder) {
        AlgoOrderExternalSnapshot detail = tryGetAlgoDetail(exchange, algoOrder);
        if (detail != null) {
            return detail;
        }

        List<AlgoOrderExternalSnapshot> pending = clientManager.getClientService(exchange.getName())
                                                               .getActiveAlgoOrders(instrument, algoOrder);
        Optional<AlgoOrderExternalSnapshot> fromPending = findMatching(pending, algoOrder);
        if (fromPending.isPresent()) {
            return fromPending.get();
        }

        List<AlgoOrderExternalSnapshot> history = clientManager.getClientService(exchange.getName())
                                                               .getAlgoOrdersHistory(instrument, algoOrder);
        return findMatching(history, algoOrder).orElse(null);
    }

    private AlgoOrderExternalSnapshot tryGetAlgoDetail(Exchange exchange, AlgoOrder algoOrder) {
        try {
            return clientManager.getClientService(exchange.getName()).getAlgoOrder(algoOrder);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Optional<AlgoOrderExternalSnapshot> findMatching(List<AlgoOrderExternalSnapshot> snapshots, AlgoOrder algoOrder) {
        if (snapshots == null) {
            return Optional.empty();
        }
        return snapshots.stream()
                        .filter(snapshot -> Objects.equals(snapshot.getExternalId(), algoOrder.getExternalId())
                                || (algoOrder.getExternalId() == null && snapshot.getExternalType() != null))
                        .findFirst();
    }

    private AlgoOrder.Status resolveAlgoStatus(String externalStatus) {
        if (externalStatus == null) {
            return AlgoOrder.Status.PENDING;
        }
        String normalized = externalStatus.toLowerCase();
        return switch (normalized) {
            case "live", "pause" -> AlgoOrder.Status.ACTIVE;
            case "effective", "canceled" -> AlgoOrder.Status.CLOSED;
            case "order_failed" -> AlgoOrder.Status.FAILED;
            default -> AlgoOrder.Status.PENDING;
        };
    }

    private AttachedAlgoOrder.Status resolveAttachedStatus(String externalStatus) {
        if (externalStatus == null) {
            return AttachedAlgoOrder.Status.ATTACHED;
        }
        String normalized = externalStatus.toLowerCase();
        return switch (normalized) {
            case "live", "pause" -> AttachedAlgoOrder.Status.ACTIVE;
            case "effective", "canceled" -> AttachedAlgoOrder.Status.CLOSED;
            case "order_failed" -> AttachedAlgoOrder.Status.FAILED;
            default -> AttachedAlgoOrder.Status.ATTACHED;
        };
    }

    private String firstNonBlank(String current, String candidate) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        return candidate;
    }
}
