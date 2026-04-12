package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.kill_switch.StateSnapshot;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_ALGO_ORDER_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_DEAL_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_ORDER_STATUSES;
import static com.example.tradingbot.domain.service.kill_switch.KillSwitchLiveStatuses.LIVE_POSITION_STATUSES;

@Component
@RequiredArgsConstructor
public class KillSwitchStateSnapshotReader {

    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final DealDataService dealDataService;
    private final KillSwitchExternalAlgoOrderReader killSwitchExternalAlgoOrderReader;

    public StateSnapshot readActionState(ClientService clientService, Instrument instrument) {
        StateSnapshot snapshot = new StateSnapshot();
        snapshot.setInternalPositions(positionDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                            LIVE_POSITION_STATUSES));
        snapshot.setInternalOrders(orderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                      LIVE_ORDER_STATUSES));
        snapshot.setInternalAlgoOrders(algoOrderDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                              LIVE_ALGO_ORDER_STATUSES));
        snapshot.setInternalDeals(dealDataService.findAllByInstrumentIdAndStatuses(instrument.getId(), LIVE_DEAL_STATUSES));
        snapshot.setExternalPositions(clientService.getPositionsByInstrument(instrument));
        snapshot.setExternalOrders(clientService.getActiveOrdersByInstrument(instrument));
        snapshot.setExternalAlgoOrders(killSwitchExternalAlgoOrderReader.readExternalAlgoOrders(clientService,
                                                                                                 instrument,
                                                                                                 snapshot.getInternalAlgoOrders()));
        return snapshot;
    }

    public StateSnapshot readReportSnapshot(ClientService clientService, Instrument instrument) {
        StateSnapshot snapshot = new StateSnapshot();
        snapshot.setInternalPositions(positionDataService.findByInstrumentId(instrument.getId()));
        snapshot.setInternalOrders(orderDataService.findByInstrumentId(instrument.getId()));
        snapshot.setInternalAlgoOrders(algoOrderDataService.findByInstrumentId(instrument.getId()));
        snapshot.setInternalDeals(dealDataService.findByInstrumentId(instrument.getId()));
        snapshot.setExternalPositions(clientService.getPositionsByInstrument(instrument));
        snapshot.setExternalOrders(clientService.getActiveOrdersByInstrument(instrument));
        snapshot.setExternalAlgoOrders(killSwitchExternalAlgoOrderReader.readExternalAlgoOrders(clientService,
                                                                                                 instrument,
                                                                                                 snapshot.getInternalAlgoOrders()));
        return snapshot;
    }
}
