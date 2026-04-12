package com.example.tradingbot.domain.service.kill_switch;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.KillSwitchResult;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.kill_switch.StateSnapshot;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import com.example.tradingbot.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.example.tradingbot.domain.model.Instrument.Status.ERROR;
import static com.example.tradingbot.domain.model.Order.Status.CLOSED;
import static com.example.tradingbot.domain.model.position.Position.CloseReason.EMERGENCY_CLOSE;
import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;
import static java.util.Objects.nonNull;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class KillSwitchService {

    private static final String RESULT_OK = "Kill-switch completed. Instrument risk fully removed.";

    private final ClientManager clientManager;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final JsonUtils jsonUtils;

    @Transactional
    public void executeKillSwitch(Deal deal) {
        Instrument instrument = instrumentDataService.findRequiredById(deal.getInstrumentId());
        Exchange exchange = exchangeDataService.findRequiredById(instrument.getExchangeId());
        executeKillSwitch(exchange, instrument, deal.getId(), "STATE_MACHINE_ERROR");
    }

    @Transactional
    public KillSwitchResult executeKillSwitch(Exchange exchange,
                                              Instrument instrument,
                                              Long dealId,
                                              String reasonCode) {
        ClientService clientService = clientManager.getClientService(exchange.getName());

        StateSnapshot before = readState(clientService, instrument);

        blockInstrument(instrument, reasonCode);
        cancelPendingOrders(clientService, instrument, before.getExternalOrders());
        closeOpenPositions(clientService, instrument, before.getExternalPositions());
        cancelPendingAlgoOrders(clientService, instrument, before.getExternalAlgoOrders());
        updateLocalState(before);

        StateSnapshot after = readState(clientService, instrument);
        String internalAfter = jsonUtils.buildInternalSnapshot(after, instrument);
        String externalAfter = jsonUtils.buildExternalSnapshot(after, instrument);

        boolean success = isSuccess(after);
        String message = success ? RESULT_OK : buildFailureMessage(after);

        log.warn("Kill-switch executed. Exchange: {}, instrument: {}, dealId: {}, reason: {}, success: {}, message: {}",
                 exchange.getName(), instrument.getExternalId(), dealId, reasonCode, success, message);

        KillSwitchResult result = new KillSwitchResult();
        result.setSuccess(success);
        result.setInternalAfter(internalAfter);
        result.setExternalAfter(externalAfter);
        result.setMessage(message);
        return result;
    }

    private void blockInstrument(Instrument instrument, String reasonCode) {
        instrument.setStatus(ERROR);
        instrumentDataService.save(instrument);
        log.warn("Kill-switch lock applied for instrument {} with reason {}", instrument.getExternalId(), reasonCode);
    }

    private void cancelPendingOrders(ClientService clientService,
                                     Instrument instrument,
                                     List<OrderExternalSnapshot> externalOrders) {
        for (OrderExternalSnapshot externalOrder : externalOrders) {
            if (externalOrder == null || externalOrder.getExternalId() == null) {
                continue;
            }
            if (!isPendingOrderSnapshot(externalOrder)) {
                continue;
            }
            Order orderToCancel = new Order();
            orderToCancel.setExternalId(externalOrder.getExternalId());
            orderToCancel.setInternalId(externalOrder.getInternalId());
            clientService.cancelOrder(orderToCancel, instrument.getExternalId());
        }
    }

    private void cancelPendingAlgoOrders(ClientService clientService,
                                         Instrument instrument,
                                         List<AlgoOrderExternalSnapshot> externalAlgoOrders) {
        for (AlgoOrderExternalSnapshot externalAlgoOrder : externalAlgoOrders) {
            if (externalAlgoOrder == null || externalAlgoOrder.getExternalId() == null) {
                continue;
            }
            if (!isLiveAlgoSnapshot(externalAlgoOrder)) {
                continue;
            }
            AlgoOrder algoToCancel = new AlgoOrder();
            algoToCancel.setExternalId(externalAlgoOrder.getExternalId());
            clientService.cancelAlgoOrder(algoToCancel, instrument.getExternalId());
        }
    }

    private void closeOpenPositions(ClientService clientService,
                                    Instrument instrument,
                                    List<PositionExternalSnapshot> externalPositions) {
        if (externalPositions.isEmpty()) {
            return;
        }
        clientService.closePositions(instrument);
    }

    private void updateLocalState(StateSnapshot before) {
        for (Position position : before.getInternalPositions()) {
            if (position == null) {
                continue;
            }
            position.toClose(EMERGENCY_CLOSE);
            positionDataService.save(position);
        }

        for (Order order : before.getInternalOrders()) {
            if (order == null) {
                continue;
            }
            order.setStatus(CLOSED);
            orderDataService.save(order);
        }

        for (AlgoOrder algoOrder : before.getInternalAlgoOrders()) {
            if (algoOrder == null) {
                continue;
            }
            algoOrder.setStatus(AlgoOrder.Status.CLOSED);
            algoOrderDataService.save(algoOrder);
        }
    }

    private StateSnapshot readState(ClientService clientService, Instrument instrument) {
        StateSnapshot snapshot = new StateSnapshot();
        snapshot.setInternalPositions(positionDataService.findByInstrumentId(instrument.getId()));
        snapshot.setInternalOrders(orderDataService.findByInstrumentId(instrument.getId()));
        snapshot.setInternalAlgoOrders(algoOrderDataService.findByInstrumentId(instrument.getId()));

        snapshot.setExternalPositions(clientService.getPositionsByInstrument(instrument));
        snapshot.setExternalOrders(clientService.getActiveOrdersByInstrument(instrument));
        snapshot.setExternalAlgoOrders(
                getExternalAlgoOrders(clientService, instrument, snapshot.getInternalAlgoOrders()));
        return snapshot;
    }

    private List<AlgoOrderExternalSnapshot> getExternalAlgoOrders(ClientService clientService,
                                                                  Instrument instrument,
                                                                  List<AlgoOrder> internalAlgoOrders) {
        Set<String> types = new LinkedHashSet<>();
        types.add("conditional");
        types.add("oco");
        types.add("trigger");
        types.add("move_order_stop");

        for (AlgoOrder internalAlgoOrder : internalAlgoOrders) {
            if (internalAlgoOrder == null) {
                continue;
            }
            String externalType = internalAlgoOrder.getExternalType();
            if (externalType == null || externalType.isBlank()) {
                continue;
            }
            types.add(externalType);
        }

        Map<String, AlgoOrderExternalSnapshot> deduplicated = new LinkedHashMap<>();
        for (String type : types) {
            AlgoOrder probe = new AlgoOrder();
            probe.setExternalType(type);
            List<AlgoOrderExternalSnapshot> externalSnapshots =
                    nullSafeList(clientService.getActiveAlgoOrders(instrument, probe));
            for (AlgoOrderExternalSnapshot externalSnapshot : externalSnapshots) {
                if (externalSnapshot == null) {
                    continue;
                }
                String key = externalSnapshot.getExternalId();
                if (key == null) {
                    key = type + "::" + deduplicated.size();
                }
                deduplicated.put(key, externalSnapshot);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private boolean isSuccess(StateSnapshot after) {
        if (isNotEmpty(after.getExternalPositions())) {
            return false;
        }
        if (containsPendingOrderSnapshot(after.getExternalOrders())) {
            return false;
        }
        if (containsLiveAlgoSnapshot(after.getExternalAlgoOrders())) {
            return false;
        }
        if (containsActivePosition(after.getInternalPositions())) {
            return false;
        }
        if (containsActiveOrder(after.getInternalOrders())) {
            return false;
        }
        return !containsLiveAlgoOrder(after.getInternalAlgoOrders());
    }

    private String buildFailureMessage(StateSnapshot after) {
        List<String> failures = new ArrayList<>();

        if (isNotEmpty(after.getExternalPositions())) {
            failures.add("external open positions=" + after.getExternalPositions()
                                                           .size());
        }
        if (containsPendingOrderSnapshot(after.getExternalOrders())) {
            failures.add("external pending orders present");
        }
        if (containsLiveAlgoSnapshot(after.getExternalAlgoOrders())) {
            failures.add("external active/pending algo-orders present");
        }
        if (containsActivePosition(after.getInternalPositions())) {
            failures.add("internal active positions present");
        }
        if (containsActiveOrder(after.getInternalOrders())) {
            failures.add("internal pending orders present");
        }
        if (containsLiveAlgoOrder(after.getInternalAlgoOrders())) {
            failures.add("internal active/pending algo-orders present");
        }

        if (failures.isEmpty()) {
            return "Kill-switch failed: unknown state mismatch.";
        }
        return "Kill-switch incomplete: " + String.join(", ", failures);
    }

    private boolean containsActivePosition(List<Position> positions) {
        for (Position position : positions) {
            if (position == null) {
                continue;
            }
            if (Position.Status.ACTIVE == position.getStatus()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsActiveOrder(List<Order> orders) {
        return emptyIfNull(orders).stream()
                                  .anyMatch(order -> nonNull(order) && order.isLive());
    }

    private boolean containsLiveAlgoOrder(List<AlgoOrder> algoOrders) {
        return emptyIfNull(algoOrders).stream()
                                      .anyMatch(algoOrder -> nonNull(algoOrder) && algoOrder.isLive());
    }

    private boolean containsPendingOrderSnapshot(List<OrderExternalSnapshot> snapshots) {
        return emptyIfNull(snapshots).stream()
                                     .anyMatch(this::isPendingOrderSnapshot);
    }

    private boolean isPendingOrderSnapshot(OrderExternalSnapshot snapshot) {
        return nonNull(snapshot)
                && nonNull(snapshot.getExternalStatus())
                && Objects.equals("live", snapshot.getExternalStatus()
                                                  .toLowerCase());
    }

    private boolean containsLiveAlgoSnapshot(List<AlgoOrderExternalSnapshot> snapshots) {
        return emptyIfNull(snapshots).stream()
                                     .anyMatch(this::isLiveAlgoSnapshot);
    }

    private boolean isLiveAlgoSnapshot(AlgoOrderExternalSnapshot snapshot) {
        return nonNull(snapshot)
                && nonNull(snapshot.getExternalStatus())
                && (
                Objects.equals("live", snapshot.getExternalStatus()
                                               .toLowerCase())
                        || Objects.equals("pause", snapshot.getExternalStatus()
                                                           .toLowerCase())
        );
    }

    private <T> List<T> nullSafeList(List<T> items) {
        if (items == null) {
            return List.of();
        }
        return items;
    }

}
