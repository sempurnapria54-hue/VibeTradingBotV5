package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.rest.model.request.ClosePositionRequest;
import com.example.tradingbot.rest.model.response.ClosePositionResponse;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.PositionEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PositionCommandService {

    private static final String POSITION_STATUS_UPDATED = "UPDATED";
    private static final String DEFAULT_MARGIN_MODE = "cross";

    private final TradingGuardService tradingGuardService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OkxTradeProxyService okxTradeProxyService;

    @Transactional
    public ClosePositionResponse closePosition(ClosePositionRequest command) {
        tradingGuardService.assertTradingAllowed(command.getExchangeId(), command.getInstrumentId());

        ExchangeEntity exchange = exchangeDataService.findById(command.getExchangeId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));
        InstrumentEntity instrument = instrumentDataService.findById(command.getInstrumentId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", "Instrument not found"));

        com.example.tradingbot.client.model.okx.ClosePositionRequest request = new com.example.tradingbot.client.model.okx.ClosePositionRequest();
        request.setInstrumentId(instrument.getExternalId());
        request.setMarginMode(DEFAULT_MARGIN_MODE);
        request.setPositionSide(command.getPositionSide());

        PositionResponse position = extractFirstPosition(okxTradeProxyService.closePosition(request));

        PositionEntity positionEntity = new PositionEntity();
        positionEntity.setExchange(exchange);
        positionEntity.setInstrument(instrument);
        positionEntity.setStatus(POSITION_STATUS_UPDATED);
        positionEntity.setPositionSide(position.getPosSide());
        positionEntity.setPositionSize(position.getPos());
        positionEntity.setAveragePrice(position.getAvgPx());
        positionEntity.setMarkPrice(position.getMarkPx());
        positionEntity.setLiquidationPrice(position.getLiqPx());
        positionEntity.setLeverage(position.getLever());
        positionEntity.setMarginMode(position.getMgnMode());
        positionEntity.setUnrealizedProfit(position.getUpl());
        positionEntity.setUpdateTime(parseLongSafe(position.getUTime()));
        positionDataService.save(positionEntity);

        return new ClosePositionResponse(position.getInstId(), position.getPosSide(), position.getUTime());
    }

    private PositionResponse extractFirstPosition(List<PositionResponse> positions) {
        if (positions.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty close-position response");
        }
        return positions.getFirst();
    }

    private Long parseLongSafe(String source) {
        if (Objects.isNull(source) || source.isBlank()) {
            return null;
        }
        return Long.parseLong(source);
    }
}
