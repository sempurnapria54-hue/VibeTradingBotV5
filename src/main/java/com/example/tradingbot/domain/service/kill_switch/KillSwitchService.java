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
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.kill_switch.executor.CancelAlgoOrderExecutor;
import com.example.tradingbot.domain.service.kill_switch.executor.CancelOrderExecutor;
import com.example.tradingbot.domain.service.kill_switch.executor.ClosePositionExecutor;
import com.example.tradingbot.domain.service.kill_switch.executor.DealEmergencyFinalizer;
import com.example.tradingbot.domain.service.kill_switch.reader.KillSwitchStateSnapshotReader;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.domain.model.Instrument.Status.ERROR;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_DEAL_STATUSES;
import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;
import static java.util.Objects.nonNull;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class KillSwitchService {

    private static final String RESULT_OK = "Kill-switch completed. Instrument risk fully removed.";

    private final ClientManager clientManager;
    private final InstrumentService instrumentService;
    private final ExchangeDataService exchangeDataService;
    private final CancelOrderExecutor cancelOrderExecutor;
    private final CancelAlgoOrderExecutor cancelAlgoOrderExecutor;
    private final ClosePositionExecutor closePositionExecutor;
    private final DealEmergencyFinalizer dealEmergencyFinalizer;
    private final KillSwitchStateSnapshotReader killSwitchStateSnapshotReader;
    private final JsonUtils jsonUtils;

    @Transactional
    public void executeKillSwitch(Deal deal) {
        Instrument instrument = instrumentService.getRequiredById(deal.getInstrumentId());
        Exchange exchange = exchangeDataService.findRequiredById(instrument.getExchangeId());
        executeKillSwitch(exchange, instrument, deal.getId(), "STATE_MACHINE_ERROR");
    }

    @Transactional
    public KillSwitchResult executeKillSwitch(Exchange exchange,
                                              Instrument instrument,
                                              Long dealId,
                                              String reasonCode) {
        ClientService clientService = clientManager.getClientService(exchange.getName());

        StateSnapshot actionState = killSwitchStateSnapshotReader.readActionState(clientService, instrument);

        blockInstrument(instrument, reasonCode);
        cancelOrderExecutor.execute(clientService, instrument, actionState.getInternalOrders());
        cancelAlgoOrderExecutor.execute(clientService, instrument, actionState.getInternalAlgoOrders());
        closePositionExecutor.execute(clientService,
                                      instrument,
                                      actionState.getInternalPositions(),
                                      isNotEmpty(actionState.getExternalPositions()));
        dealEmergencyFinalizer.execute(actionState.getInternalDeals());

        StateSnapshot reportAfter = killSwitchStateSnapshotReader.readReportSnapshot(clientService, instrument);

        String internalAfter = jsonUtils.buildInternalSnapshot(reportAfter, instrument);
        String externalAfter = jsonUtils.buildExternalSnapshot(reportAfter, instrument);

        boolean success = isSuccess(reportAfter);
        String message = success ? RESULT_OK : buildFailureMessage(reportAfter);

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
        instrumentService.blockByKillSwitch(instrument);
        log.warn("Kill-switch lock applied for instrument {} with reason {}. New status: {}",
                 instrument.getExternalId(),
                 reasonCode,
                 ERROR);
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
        if (containsLiveAlgoOrder(after.getInternalAlgoOrders())) {
            return false;
        }
        return !containsLiveDeal(after.getInternalDeals());
    }

    private String buildFailureMessage(StateSnapshot after) {
        List<String> failures = new ArrayList<>();

        if (isNotEmpty(after.getExternalPositions())) {
            failures.add("external open positions=" + after.getExternalPositions().size());
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
        if (containsLiveDeal(after.getInternalDeals())) {
            failures.add("internal active deals present");
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
        return emptyIfNull(orders).stream().anyMatch(order -> nonNull(order) && order.isLive());
    }

    private boolean containsLiveAlgoOrder(List<AlgoOrder> algoOrders) {
        return emptyIfNull(algoOrders).stream().anyMatch(algoOrder -> nonNull(algoOrder) && algoOrder.isLive());
    }

    private boolean containsLiveDeal(List<Deal> deals) {
        return emptyIfNull(deals).stream().anyMatch(this::isLiveDeal);
    }

    private boolean isLiveDeal(Deal deal) {
        return nonNull(deal)
                && nonNull(deal.getStatus())
                && LIVE_DEAL_STATUSES.contains(deal.getStatus().name());
    }

    private boolean containsPendingOrderSnapshot(List<OrderExternalSnapshot> snapshots) {
        return emptyIfNull(snapshots).stream().anyMatch(this::isPendingOrderSnapshot);
    }

    private boolean isPendingOrderSnapshot(OrderExternalSnapshot snapshot) {
        return nonNull(snapshot)
                && nonNull(snapshot.getExternalStatus())
                && Objects.equals("live", snapshot.getExternalStatus().toLowerCase());
    }

    private boolean containsLiveAlgoSnapshot(List<AlgoOrderExternalSnapshot> snapshots) {
        return emptyIfNull(snapshots).stream().anyMatch(this::isLiveAlgoSnapshot);
    }

    private boolean isLiveAlgoSnapshot(AlgoOrderExternalSnapshot snapshot) {
        return nonNull(snapshot)
                && nonNull(snapshot.getExternalStatus())
                && (
                Objects.equals("live", snapshot.getExternalStatus().toLowerCase())
                        || Objects.equals("pause", snapshot.getExternalStatus().toLowerCase())
        );
    }
}
