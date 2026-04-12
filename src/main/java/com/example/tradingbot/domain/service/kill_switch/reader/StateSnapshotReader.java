package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.kill_switch.StateSnapshot;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_ALGO_ORDER_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_DEAL_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_ORDER_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_POSITION_STATUSES;
import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Component
@RequiredArgsConstructor
public class StateSnapshotReader {

    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final DealDataService dealDataService;
    private final ExternalAlgoOrderReader externalAlgoOrderReader;

    public StateSnapshot readBeforeSnapshot(ClientService clientService, Instrument instrument) {
        StateSnapshot snapshot = new StateSnapshot();
        snapshot.setInternalPositions(positionDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                           LIVE_POSITION_STATUSES));
        snapshot.setInternalOrders(orderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                     LIVE_ORDER_STATUSES));
        snapshot.setInternalAlgoOrders(algoOrderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                             LIVE_ALGO_ORDER_STATUSES));
        snapshot.setInternalDeals(
                dealDataService.findAllByInstrumentIdAndStatuses(instrument.getId(), LIVE_DEAL_STATUSES));
        snapshot.setExternalPositions(clientService.getPositionsByInstrument(instrument));
        snapshot.setExternalOrders(clientService.getActiveOrdersByInstrument(instrument));
        snapshot.setExternalAlgoOrders(externalAlgoOrderReader.readExternalAlgoOrders(clientService,
                                                                                      instrument,
                                                                                      snapshot.getInternalAlgoOrders()));
        return snapshot;
    }

    public StateSnapshot readAfterSnapshot(ClientService clientService,
                                           Instrument instrument,
                                           StateSnapshot beforeSnapshot) {
        StateSnapshot safeBeforeSnapshot = beforeSnapshot;
        if (safeBeforeSnapshot == null) {
            safeBeforeSnapshot = new StateSnapshot();
        }
        StateSnapshot snapshot = new StateSnapshot();
        snapshot.setInternalPositions(positionDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                           LIVE_POSITION_STATUSES));
        snapshot.setInternalOrders(orderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                     LIVE_ORDER_STATUSES));
        snapshot.setInternalAlgoOrders(algoOrderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                             LIVE_ALGO_ORDER_STATUSES));
        snapshot.setInternalDeals(dealDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                   LIVE_DEAL_STATUSES));
        snapshot.setInternalRelatedInactivePositions(resolveRelatedInactivePositions(safeBeforeSnapshot));
        snapshot.setInternalRelatedInactiveOrders(resolveRelatedInactiveOrders(safeBeforeSnapshot));
        snapshot.setInternalRelatedInactiveAlgoOrders(resolveRelatedInactiveAlgoOrders(safeBeforeSnapshot));
        snapshot.setInternalRelatedInactiveDeals(resolveRelatedInactiveDeals(instrument, safeBeforeSnapshot));
        snapshot.setExternalPositions(clientService.getPositionsByInstrument(instrument));
        snapshot.setExternalOrders(clientService.getActiveOrdersByInstrument(instrument));
        snapshot.setExternalAlgoOrders(externalAlgoOrderReader.readExternalAlgoOrders(clientService,
                                                                                      instrument,
                                                                                      snapshot.getInternalAlgoOrders()));
        snapshot.setExternalRelatedInactivePositions(List.of());
        snapshot.setExternalRelatedInactiveOrders(List.of());
        snapshot.setExternalRelatedInactiveAlgoOrders(List.of());
        return snapshot;
    }

    private List<Position> resolveRelatedInactivePositions(StateSnapshot beforeSnapshot) {
        List<Position> relatedInactivePositions = new ArrayList<>();
        for (Position position : emptyIfNull(beforeSnapshot.getInternalPositions())) {
            if (position == null) {
                continue;
            }
            if (position.isLive()) {
                continue;
            }
            relatedInactivePositions.add(position);
        }
        return relatedInactivePositions;
    }

    private List<Order> resolveRelatedInactiveOrders(StateSnapshot beforeSnapshot) {
        List<Order> relatedInactiveOrders = new ArrayList<>();
        for (Order order : emptyIfNull(beforeSnapshot.getInternalOrders())) {
            if (order == null) {
                continue;
            }
            if (order.isLive()) {
                continue;
            }
            relatedInactiveOrders.add(order);
        }
        return relatedInactiveOrders;
    }

    private List<AlgoOrder> resolveRelatedInactiveAlgoOrders(StateSnapshot beforeSnapshot) {
        List<AlgoOrder> relatedInactiveAlgoOrders = new ArrayList<>();
        for (AlgoOrder algoOrder : emptyIfNull(beforeSnapshot.getInternalAlgoOrders())) {
            if (algoOrder == null) {
                continue;
            }
            if (algoOrder.isLive()) {
                continue;
            }
            relatedInactiveAlgoOrders.add(algoOrder);
        }
        return relatedInactiveAlgoOrders;
    }

    private List<Deal> resolveRelatedInactiveDeals(Instrument instrument, StateSnapshot beforeSnapshot) {
        List<Deal> relatedInactiveDeals = new ArrayList<>();
        for (Deal deal : emptyIfNull(beforeSnapshot.getInternalDeals())) {
            if (deal == null) {
                continue;
            }
            if (isLiveDeal(deal)) {
                continue;
            }
            relatedInactiveDeals.add(deal);
        }

        dealDataService.findLatestByInstrumentId(instrument.getId())
                       .ifPresent(latestDeal -> {
                           if (isLiveDeal(latestDeal)) {
                               return;
                           }
                           if (containsDeal(relatedInactiveDeals, latestDeal)) {
                               return;
                           }
                           relatedInactiveDeals.add(0, latestDeal);
                       });
        return relatedInactiveDeals;
    }

    private boolean containsDeal(List<Deal> deals, Deal candidate) {
        for (Deal deal : deals) {
            if (deal == null || deal.getId() == null || candidate.getId() == null) {
                continue;
            }
            if (Objects.equals(deal.getId(), candidate.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isLiveDeal(Deal deal) {
        return deal != null
                && deal.getStatus() != null
                && LIVE_DEAL_STATUSES.contains(deal.getStatus().name());
    }
}
