package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.model.okx.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.domain.model.trading.ClosePositionCommand;
import com.example.tradingbot.domain.model.trading.ClosePositionResult;
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

        PositionResponse position = extractFirstPosition(okxTradeProxyService.closePosition(request));

        PositionEntity positionEntity = new PositionEntity();
        positionEntity.setExchange(exchange);
        positionEntity.setInstrument(instrument);
        positionEntity.setStatus(POSITION_STATUS_UPDATED);
        positionEntity.setSide(position.getPosSide());
        positionEntity.setPos(position.getPos());
        positionEntity.setAvgPx(position.getAvgPx());
        positionEntity.setMarkPx(position.getMarkPx());
        positionEntity.setLiqPx(position.getLiqPx());
        positionEntity.setLever(position.getLever());
        positionEntity.setMgnMode(position.getMgnMode());
        positionEntity.setUpl(position.getUpl());
        positionEntity.setUTime(parseLongSafe(position.getUTime()));
        positionDataService.save(positionEntity);

        return new ClosePositionResult(position.getInstId(), position.getPosSide(), position.getUTime());
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
