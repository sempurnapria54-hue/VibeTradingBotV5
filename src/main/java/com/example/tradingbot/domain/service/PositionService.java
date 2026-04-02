package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class PositionService {

    private static final String POSITION_STATUS_UPDATED = "UPDATED";

    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OkxProxyService okxProxyService;
    private final ClientManager clientManager;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;
    private final PositionMapper mapper;

//    @Transactional
//    public ClosePositionResponse closePosition(ClosePositionRequest command) {
//        ExchangeEntity exchangeEntity = exchangeDataService.findRequiredByInternalId(command.getExchangeId());
//        InstrumentEntity instrumentEntity = instrumentDataService.findRequiredByExchangeIdAndInternalId(
//                exchangeEntity.getId(), command.getInstrumentId());
//        tradingGuardService.assertTradingAllowed(exchangeEntity, instrumentEntity);
//
//        com.example.tradingbot.client.model.okx.request.ClosePositionRequest request = new com.example.tradingbot.client.model.okx.request.ClosePositionRequest();
//        request.setInstrumentId(instrumentEntity.getExternalId());
//        request.setMarginMode(DEFAULT_TRADE_MODE);
//        request.setPositionSide(command.getPositionSide());
//
//        PositionResponse position = extractFirstPosition(okxProxyService.closePosition(request));
//
//        PositionEntity positionEntity = new PositionEntity();
//        positionEntity.setInstrumentId(instrumentEntity.getId());
//        positionEntity.setStatus(POSITION_STATUS_UPDATED);
//        positionEntity.setPositionSide(position.getPosSide());
//        positionEntity.setPositionSize(position.getPos());
//        positionEntity.setAveragePrice(position.getAvgPx());
//        positionEntity.setMarkPrice(position.getMarkPx());
//        positionEntity.setLiquidationPrice(position.getLiqPx());
//        positionEntity.setLeverage(position.getLever());
//        positionEntity.setMarginMode(position.getMgnMode());
//        positionEntity.setUnrealizedProfit(position.getUpl());
//        positionEntity.setExchangeModifiedAt(parseOffsetDateTimeFromMillisSafe(position.getuTime()));
//        positionDataService.save(positionEntity);
//
//        return new ClosePositionResponse(position.getInstId(), position.getPosSide(), position.getuTime());
//    }

    private PositionResponse extractFirstPosition(List<PositionResponse> positions) {
        if (positions.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE",
                                              "OKX returned empty close-position response");
        }
        return positions.getFirst();
    }

    public Position refreshPosition(Deal deal) {
        List<Position> positions = deal.getPositions();
        if (isEmpty(positions) || positions.size() > 1) {
            throw new RuntimeException("Invalid positions in deal");
        }
        Position currentPosition = positions.getFirst();
        Position saved = positionDataService.findByIdRequired(currentPosition.getId());
        Instrument instrument = instrumentService.getRequiredById(deal.getInstrumentId());
        Exchange exchange = exchangeService.getRequiredById(instrument.getExchangeId());

        List<PositionExternalSnapshot> externalSnapshots = clientManager.getClientService(exchange.getName())
                                                                        .getPositionsByInstrument(instrument);

        if (isNotEmpty(externalSnapshots) && externalSnapshots.size() > 1) {
            throw new RuntimeException("Invalid positions in deal");
        }

        mapper.updateDomainFromExternalSnapshot(externalSnapshots.getFirst(), saved);
        return positionDataService.save(saved);
    }
}
