package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class TradingGuardService {

    private static final String EXCHANGE_STATUS_ACTIVE = "ACTIVE";
    private static final String INSTRUMENT_STATUS_ACTIVE = "ACTIVE";
    private static final String INSTRUMENT_STATUS_SYNC = "SYNC";
    private static final String INSTRUMENT_STATUS_HOLD = "HOLD";

    private static final String EXCHANGE_NOT_ACTIVE = "EXCHANGE_NOT_ACTIVE";
    private static final String INSTRUMENT_NOT_READY = "INSTRUMENT_NOT_READY";
    private static final String INSTRUMENT_SYNC = "INSTRUMENT_SYNC";
    private static final String INSTRUMENT_HOLD = "INSTRUMENT_HOLD";

    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;

    public void assertTradingAllowed(Long exchangeId, Long instrumentId) {
        ExchangeEntity exchange = exchangeDataService.findById(exchangeId)
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));

        if (isFalse(Objects.equals(exchange.getStatus(), EXCHANGE_STATUS_ACTIVE))) {
            throw new TradingCommandException(HttpStatus.CONFLICT, EXCHANGE_NOT_ACTIVE, "Exchange is not ACTIVE");
        }

        InstrumentEntity instrument = instrumentDataService.findById(instrumentId)
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", "Instrument not found"));

        if (BooleanUtils.isTrue(Objects.equals(instrument.getExchangeId(), exchangeId))) {
            assertInstrumentIsActive(instrument);
            return;
        }

        throw new TradingCommandException(HttpStatus.CONFLICT, INSTRUMENT_NOT_READY, "Instrument does not belong to exchange");
    }

    public void assertTradingAllowed(ExchangeEntity exchange, InstrumentEntity instrument) {
        if (isFalse(Objects.equals(exchange.getStatus(), EXCHANGE_STATUS_ACTIVE))) {
            throw new TradingCommandException(HttpStatus.CONFLICT, EXCHANGE_NOT_ACTIVE, "Exchange is not ACTIVE");
        }

        if (Objects.equals(instrument.getExchangeId(), exchange.getId())) {
            assertInstrumentIsActive(instrument);
            return;
        }

        throw new TradingCommandException(HttpStatus.CONFLICT, INSTRUMENT_NOT_READY, "Instrument does not belong to exchange");
    }

    private void assertInstrumentIsActive(InstrumentEntity instrument) {
        String status = instrument.getStatus();
        if (BooleanUtils.isTrue(Objects.equals(status, INSTRUMENT_STATUS_ACTIVE))) {
            return;
        }
        if (BooleanUtils.isTrue(Objects.equals(status, INSTRUMENT_STATUS_SYNC))) {
            throw new TradingCommandException(HttpStatus.CONFLICT, INSTRUMENT_SYNC, "Instrument is in SYNC status");
        }
        if (BooleanUtils.isTrue(Objects.equals(status, INSTRUMENT_STATUS_HOLD))) {
            throw new TradingCommandException(HttpStatus.CONFLICT, INSTRUMENT_HOLD, "Instrument is in HOLD status");
        }
        throw new TradingCommandException(HttpStatus.CONFLICT, INSTRUMENT_NOT_READY, "Instrument is not ready for trading");
    }
}
