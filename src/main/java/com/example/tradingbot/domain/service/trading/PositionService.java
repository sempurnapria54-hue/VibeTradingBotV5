package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.PositionEntity;
import com.example.tradingbot.domain.service.OkxProxyService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import com.example.tradingbot.rest.model.request.ClosePositionRequest;
import com.example.tradingbot.rest.model.response.ClosePositionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.tradingbot.util.Constant.Service.DEFAULT_TRADE_MODE;
import static com.example.tradingbot.util.NumberUtils.parseOffsetDateTimeFromMillisSafe;

@Service
@RequiredArgsConstructor
public class PositionService {

    private static final String POSITION_STATUS_UPDATED = "UPDATED";

    private final TradingGuardService tradingGuardService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OkxProxyService okxProxyService;

    @Transactional
    public ClosePositionResponse closePosition(ClosePositionRequest command) {
        ExchangeEntity exchangeEntity = exchangeDataService.findRequiredByInternalId(command.getExchangeId());
        InstrumentEntity instrumentEntity = instrumentDataService.findRequiredByExchangeIdAndInternalId(exchangeEntity.getId(), command.getInstrumentId());
        tradingGuardService.assertTradingAllowed(exchangeEntity, instrumentEntity);

        com.example.tradingbot.client.model.okx.ClosePositionRequest request = new com.example.tradingbot.client.model.okx.ClosePositionRequest();
        request.setInstrumentId(instrumentEntity.getExternalId());
        request.setMarginMode(DEFAULT_TRADE_MODE);
        request.setPositionSide(command.getPositionSide());

        PositionResponse position = extractFirstPosition(okxProxyService.closePosition(request));

        PositionEntity positionEntity = new PositionEntity();
        positionEntity.setExchangeId(exchangeEntity.getId());
        positionEntity.setInstrumentId(instrumentEntity.getId());
        positionEntity.setStatus(POSITION_STATUS_UPDATED);
        positionEntity.setPositionSide(position.getPosSide());
        positionEntity.setPositionSize(position.getPos());
        positionEntity.setAveragePrice(position.getAvgPx());
        positionEntity.setMarkPrice(position.getMarkPx());
        positionEntity.setLiquidationPrice(position.getLiqPx());
        positionEntity.setLeverage(position.getLever());
        positionEntity.setMarginMode(position.getMgnMode());
        positionEntity.setUnrealizedProfit(position.getUpl());
        positionEntity.setExchangeModifiedAt(parseOffsetDateTimeFromMillisSafe(position.getuTime()));
        positionDataService.save(positionEntity);

        return new ClosePositionResponse(position.getInstId(), position.getPosSide(), position.getuTime());
    }

    private PositionResponse extractFirstPosition(List<PositionResponse> positions) {
        if (positions.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty close-position response");
        }
        return positions.getFirst();
    }
}
