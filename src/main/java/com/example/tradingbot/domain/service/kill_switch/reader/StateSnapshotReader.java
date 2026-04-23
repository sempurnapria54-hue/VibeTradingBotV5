package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.kill_switch.StateSnapshot;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_ALGO_ORDER_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_DEAL_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_ORDER_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_POSITION_STATUSES;
import static com.example.tradingbot.util.CollectionUtils.doNotContains;
import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;
import static java.util.Objects.isNull;

@Component
@RequiredArgsConstructor
public class StateSnapshotReader {

    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final DealDataService dealDataService;
    private final ExternalPositionSnapshotReader externalPositionSnapshotReader;
    private final ExternalOrderSnapshotReader externalOrderSnapshotReader;
    private final ExternalAlgoOrderSnapshotReader externalAlgoOrderSnapshotReader;

    public StateSnapshot readBeforeSnapshot(ClientService clientService, Instrument instrument) {
        StateSnapshot snapshot = new StateSnapshot();
        snapshot.setInternalPositions(readActiveInternalPositions(instrument));
        snapshot.setInternalOrders(readActiveInternalOrders(instrument));
        snapshot.setInternalAlgoOrders(readActiveInternalAlgoOrders(instrument));
        snapshot.setInternalDeals(readActiveInternalDeals(instrument));

        snapshot.setInternalRelatedInactivePositions(List.of());
        snapshot.setInternalRelatedInactiveOrders(List.of());
        snapshot.setInternalRelatedInactiveAlgoOrders(List.of());
        snapshot.setInternalRelatedInactiveDeals(List.of());

        snapshot.setExternalPositions(externalPositionSnapshotReader.readActivePositions(clientService, instrument));
        snapshot.setExternalOrders(externalOrderSnapshotReader.readActiveOrders(clientService, instrument));
        snapshot.setExternalAlgoOrders(externalAlgoOrderSnapshotReader.readActiveAlgoOrders(clientService,
                                                                                            instrument,
                                                                                            snapshot.getInternalAlgoOrders()));

        snapshot.setExternalRelatedInactiveOrders(List.of());
        snapshot.setExternalRelatedInactiveAlgoOrders(List.of());
        return snapshot;
    }

    public StateSnapshot readAfterSnapshot(ClientService clientService,
                                           Instrument instrument,
                                           StateSnapshot beforeSnapshot) {
        StateSnapshot safeBeforeSnapshot = beforeSnapshot;
        if (isNull(safeBeforeSnapshot)) {
            safeBeforeSnapshot = new StateSnapshot();
        }

        StateSnapshot snapshot = new StateSnapshot();
        snapshot.setInternalPositions(readActiveInternalPositions(instrument));
        snapshot.setInternalOrders(readActiveInternalOrders(instrument));
        snapshot.setInternalAlgoOrders(readActiveInternalAlgoOrders(instrument));
        snapshot.setInternalDeals(readActiveInternalDeals(instrument));

        snapshot.setInternalRelatedInactivePositions(resolveRelatedInactivePositions(safeBeforeSnapshot));
        snapshot.setInternalRelatedInactiveOrders(resolveRelatedInactiveOrders(safeBeforeSnapshot));
        snapshot.setInternalRelatedInactiveAlgoOrders(resolveRelatedInactiveAlgoOrders(safeBeforeSnapshot));
        snapshot.setInternalRelatedInactiveDeals(resolveRelatedInactiveDeals(instrument, safeBeforeSnapshot));

        snapshot.setExternalPositions(externalPositionSnapshotReader.readActivePositions(clientService, instrument));
        snapshot.setExternalOrders(externalOrderSnapshotReader.readActiveOrders(clientService, instrument));
        snapshot.setExternalAlgoOrders(externalAlgoOrderSnapshotReader.readActiveAlgoOrders(clientService,
                                                                                            instrument,
                                                                                            snapshot.getInternalAlgoOrders()));

        snapshot.setExternalRelatedInactiveOrders(
                externalOrderSnapshotReader.readRelatedInactiveOrders(clientService, instrument));
        snapshot.setExternalRelatedInactiveAlgoOrders(
                externalAlgoOrderSnapshotReader.readRelatedInactiveAlgoOrders(clientService,
                                                                              instrument,
                                                                              safeBeforeSnapshot.getInternalAlgoOrders()));
        return snapshot;
    }

    private List<Position> readActiveInternalPositions(Instrument instrument) {
        return positionDataService.findAllByInstrumentIdAndStatuses(instrument.getId(), LIVE_POSITION_STATUSES);
    }

    private List<Order> readActiveInternalOrders(Instrument instrument) {
        return orderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(), LIVE_ORDER_STATUSES);
    }

    private List<AlgoOrder> readActiveInternalAlgoOrders(Instrument instrument) {
        return algoOrderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(), LIVE_ALGO_ORDER_STATUSES);
    }

    private List<Deal> readActiveInternalDeals(Instrument instrument) {
        return dealDataService.findAllByInstrumentIdAndStatuses(instrument.getId(), LIVE_DEAL_STATUSES);
    }

    private List<Position> resolveRelatedInactivePositions(StateSnapshot beforeSnapshot) {
        return emptyIfNull(beforeSnapshot.getInternalPositions()).stream()
                                                                 .filter(Objects::nonNull)
                                                                 .filter(Position::isNotLive)
                                                                 .toList();
    }

    private List<Order> resolveRelatedInactiveOrders(StateSnapshot beforeSnapshot) {
        return emptyIfNull(beforeSnapshot.getInternalOrders()).stream()
                                                              .filter(Objects::nonNull)
                                                              .filter(Order::isNotLive)
                                                              .toList();
    }

    private List<AlgoOrder> resolveRelatedInactiveAlgoOrders(StateSnapshot beforeSnapshot) {
        return emptyIfNull(beforeSnapshot.getInternalAlgoOrders()).stream()
                                                                  .filter(Objects::nonNull)
                                                                  .filter(AlgoOrder::isNotLive)
                                                                  .toList();
    }

    private List<Deal> resolveRelatedInactiveDeals(Instrument instrument, StateSnapshot beforeSnapshot) {
        List<Deal> beforeInactiveDeals = emptyIfNull(
                beforeSnapshot.getInternalDeals()).stream()
                                                  .filter(Objects::nonNull)
                                                  .filter(Deal::isNotLive)
                                                  .toList();

        return dealDataService.findLatestByInstrumentId(instrument.getId())
                              .filter(Deal::isNotLive)
                              .filter(latestDeal -> doNotContains(beforeInactiveDeals, latestDeal))
                              .map(latestDeal -> Stream.concat(Stream.of(latestDeal), beforeInactiveDeals.stream())
                                                       .toList())
                              .orElse(beforeInactiveDeals);
    }
}
