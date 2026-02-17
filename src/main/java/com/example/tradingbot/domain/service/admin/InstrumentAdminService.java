package com.example.tradingbot.domain.service.admin;

import com.example.tradingbot.domain.model.admin.Instrument;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InstrumentAdminService {

    private static final String DEFAULT_INSTRUMENT_STATUS = "NEW";
    private static final String DEFAULT_POSITION_MODE = "NONE";

    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;

    public Instrument createInstrument(Instrument instrument) {
        ExchangeEntity exchangeEntity = exchangeDataService.findById(instrument.getExchangeId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exchange not found"));

        if (instrumentDataService.findByExchangeIdAndInstId(instrument.getExchangeId(), instrument.getInstId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instrument already exists for exchange");
        }

        InstrumentEntity instrumentEntity = new InstrumentEntity();
        instrumentEntity.setExchange(exchangeEntity);
        instrumentEntity.setName(instrument.getInstId());
        instrumentEntity.setInstId(instrument.getInstId());
        instrumentEntity.setInstType(instrument.getInstType());
        instrumentEntity.setPositionMode(DEFAULT_POSITION_MODE);
        instrumentEntity.setStatus(DEFAULT_INSTRUMENT_STATUS);

        InstrumentEntity savedInstrument = instrumentDataService.create(instrumentEntity);
        return toDomain(savedInstrument);
    }

    public List<Instrument> list() {
        return instrumentDataService.findAll()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    public Instrument get(Long id) {
        InstrumentEntity instrumentEntity = instrumentDataService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
        return toDomain(instrumentEntity);
    }

    private Instrument toDomain(InstrumentEntity instrumentEntity) {
        Long exchangeId = instrumentEntity.getExchangeId();
        if (Objects.isNull(exchangeId) && Objects.nonNull(instrumentEntity.getExchange())) {
            exchangeId = instrumentEntity.getExchange().getId();
        }
        return new Instrument(
            instrumentEntity.getId(),
            exchangeId,
            instrumentEntity.getInstId(),
            instrumentEntity.getInstType(),
            instrumentEntity.getStatus()
        );
    }
}
