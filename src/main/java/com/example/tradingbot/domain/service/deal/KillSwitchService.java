package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.KillSwitchResult;
import com.example.tradingbot.domain.model.exchange.Exchange;
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
import static com.example.tradingbot.domain.model.Order.Status.PENDING;
import static com.example.tradingbot.domain.model.position.Position.CloseReason.EMERGENCY_CLOSE;

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
        cancelPendingOrders(clientService, instrument, before.externalOrders);
        cancelPendingAlgoOrders(clientService, instrument, before.externalAlgoOrders);
        closeOpenPositions(clientService, instrument, before.externalPositions);
        updateLocalState(before);

        StateSnapshot after = readState(clientService, instrument);
        String internalAfter = buildInternalSnapshot(after, instrument);
        String externalAfter = buildExternalSnapshot(after, instrument);

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

    public KillSwitchResult executeTradeRuleViolation(Exchange exchange,
                                                      Instrument instrument,
                                                      Long dealId,
                                                      String code) {
        return executeKillSwitch(exchange, instrument, dealId, code);
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
            algoToCancel.setInternalId(externalAlgoOrder.getInternalId());
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
        for (Position position : before.internalPositions) {
            if (position == null) {
                continue;
            }
            position.toClose(EMERGENCY_CLOSE);
            positionDataService.save(position);
        }

        for (Order order : before.internalOrders) {
            if (order == null) {
                continue;
            }
            order.setStatus(CLOSED);
            orderDataService.save(order);
        }

        for (AlgoOrder algoOrder : before.internalAlgoOrders) {
            if (algoOrder == null) {
                continue;
            }
            algoOrder.setStatus(AlgoOrder.Status.CLOSED);
            algoOrderDataService.save(algoOrder);
        }
    }

    private StateSnapshot readState(ClientService clientService, Instrument instrument) {
        StateSnapshot snapshot = new StateSnapshot();
        snapshot.internalPositions = positionDataService.findByInstrumentId(instrument.getId());
        snapshot.internalOrders = orderDataService.findByInstrumentId(instrument.getId());
        snapshot.internalAlgoOrders = algoOrderDataService.findByInstrumentId(instrument.getId());

        snapshot.externalPositions = nullSafeList(clientService.getPositionsByInstrument(instrument));
        snapshot.externalOrders = nullSafeList(clientService.getActiveOrdersByInstrument(instrument));
        snapshot.externalAlgoOrders = getExternalAlgoOrders(clientService, instrument, snapshot.internalAlgoOrders);
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
        if (!after.externalPositions.isEmpty()) {
            return false;
        }
        if (containsPendingOrderSnapshot(after.externalOrders)) {
            return false;
        }
        if (containsLiveAlgoSnapshot(after.externalAlgoOrders)) {
            return false;
        }
        if (containsActivePosition(after.internalPositions)) {
            return false;
        }
        if (containsPendingOrder(after.internalOrders)) {
            return false;
        }
        return !containsLiveAlgoOrder(after.internalAlgoOrders);
    }

    private String buildFailureMessage(StateSnapshot after) {
        List<String> failures = new ArrayList<>();

        if (!after.externalPositions.isEmpty()) {
            failures.add("external open positions=" + after.externalPositions.size());
        }
        if (containsPendingOrderSnapshot(after.externalOrders)) {
            failures.add("external pending orders present");
        }
        if (containsLiveAlgoSnapshot(after.externalAlgoOrders)) {
            failures.add("external active/pending algo-orders present");
        }
        if (containsActivePosition(after.internalPositions)) {
            failures.add("internal active positions present");
        }
        if (containsPendingOrder(after.internalOrders)) {
            failures.add("internal pending orders present");
        }
        if (containsLiveAlgoOrder(after.internalAlgoOrders)) {
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

    private boolean containsPendingOrder(List<Order> orders) {
        for (Order order : orders) {
            if (order == null) {
                continue;
            }
            if (PENDING == order.getStatus()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLiveAlgoOrder(List<AlgoOrder> algoOrders) {
        for (AlgoOrder algoOrder : algoOrders) {
            if (algoOrder == null) {
                continue;
            }
            if (AlgoOrder.Status.ACTIVE == algoOrder.getStatus() || AlgoOrder.Status.PENDING == algoOrder.getStatus()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPendingOrderSnapshot(List<OrderExternalSnapshot> snapshots) {
        for (OrderExternalSnapshot snapshot : snapshots) {
            if (isPendingOrderSnapshot(snapshot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPendingOrderSnapshot(OrderExternalSnapshot snapshot) {
        if (snapshot == null || snapshot.getExternalStatus() == null) {
            return false;
        }
        return Objects.equals("live", snapshot.getExternalStatus().toLowerCase());
    }

    private boolean containsLiveAlgoSnapshot(List<AlgoOrderExternalSnapshot> snapshots) {
        for (AlgoOrderExternalSnapshot snapshot : snapshots) {
            if (isLiveAlgoSnapshot(snapshot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLiveAlgoSnapshot(AlgoOrderExternalSnapshot snapshot) {
        if (snapshot == null || snapshot.getExternalStatus() == null) {
            return false;
        }
        String externalStatus = snapshot.getExternalStatus().toLowerCase();
        return Objects.equals("live", externalStatus) || Objects.equals("pause", externalStatus);
    }

    private String buildInternalSnapshot(StateSnapshot snapshot, Instrument instrument) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instrument", instrument);
        payload.put("positions", snapshot.internalPositions);
        payload.put("orders", snapshot.internalOrders);
        payload.put("algoOrders", snapshot.internalAlgoOrders);
        return jsonUtils.toJson(payload);
    }

    private String buildExternalSnapshot(StateSnapshot snapshot, Instrument instrument) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instrumentExternalId", instrument.getExternalId());
        payload.put("positions", snapshot.externalPositions);
        payload.put("orders", snapshot.externalOrders);
        payload.put("algoOrders", snapshot.externalAlgoOrders);
        return jsonUtils.toJson(payload);
    }

    private <T> List<T> nullSafeList(List<T> items) {
        if (items == null) {
            return List.of();
        }
        return items;
    }

    private static class StateSnapshot {
        private List<Position> internalPositions;
        private List<Order> internalOrders;
        private List<AlgoOrder> internalAlgoOrders;
        private List<PositionExternalSnapshot> externalPositions;
        private List<OrderExternalSnapshot> externalOrders;
        private List<AlgoOrderExternalSnapshot> externalAlgoOrders;
    }
}
