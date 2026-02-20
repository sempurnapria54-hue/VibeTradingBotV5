package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.domain.model.okxproxy.ClosePositionRequest;
import com.example.tradingbot.domain.model.okxproxy.Position;
import com.example.tradingbot.domain.model.trading.ClosePositionCommand;
import com.example.tradingbot.domain.model.trading.ClosePositionResult;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
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
    public ClosePositionResult closePosition(ClosePositionCommand command) {
        tradingGuardService.assertTradingAllowed(command.getExchangeId(), command.getInstrumentId());

        ExchangeEntity exchange = exchangeDataService.findById(command.getExchangeId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));
        InstrumentEntity instrument = instrumentDataService.findById(command.getInstrumentId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", "Instrument not found"));

        ClosePositionRequest request = new ClosePositionRequest();
        request.setInstrumentId(instrument.getExternalName());
        request.setMarginMode(DEFAULT_MARGIN_MODE);
        request.setPositionSide(command.getPositionSide());

        Position position = extractFirstPosition(okxTradeProxyService.closePosition(request));

        PositionEntity positionEntity = new PositionEntity();
        positionEntity.setExchange(exchange);
        positionEntity.setInstrument(instrument);
        positionEntity.setStatus(POSITION_STATUS_UPDATED);
        positionEntity.setSide(position.getPositionSide());
        positionEntity.setPos(position.getPositionSize());
        positionEntity.setAvgPx(position.getAveragePrice());
        positionEntity.setMarkPx(position.getMarkPrice());
        positionEntity.setLiqPx(position.getLiquidationPrice());
        positionEntity.setLever(position.getLeverage());
        positionEntity.setMgnMode(position.getMarginMode());
        positionEntity.setUpl(position.getUnrealizedProfit());
        positionEntity.setUTime(parseLongSafe(position.getUpdateTime()));
        positionDataService.save(positionEntity);

        return new ClosePositionResult(position.getInstrumentId(), position.getPositionSide(), position.getUpdateTime());
    }

    private Position extractFirstPosition(List<Position> positions) {
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
