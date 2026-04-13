package com.example.tradingbot.util.factory;

import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import com.example.tradingbot.rest.model.request.instrument.CreateInstrumentRequest;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

import static com.example.tradingbot.util.Constant.Service.DEFAULT_POSITION_MODE;
import static com.example.tradingbot.util.Constant.Status.Instrument.INSTRUMENT_STATUS_CREATED;
import static com.example.tradingbot.util.factory.CandleGroupFactory.createCandleGroupEntity;
import static java.util.stream.Collectors.toList;

@UtilityClass
public class InstrumentFactory {

    public static InstrumentEntity createInstrumentEntity(Long exchangeId, CreateInstrumentRequest request) {
        InstrumentEntity instrumentEntity = new InstrumentEntity();
        instrumentEntity.setInternalId(UUID.randomUUID()
                                           .toString());
        instrumentEntity.setExternalType(request.getType());
        instrumentEntity.setExternalId(request.getExternalId());
        instrumentEntity.setExchangeId(exchangeId);
        instrumentEntity.setPositionMode(DEFAULT_POSITION_MODE);
        instrumentEntity.setStatus(INSTRUMENT_STATUS_CREATED);
        List<CandleGroupEntity> groupEntities = request.getCandleGroups()
                                                       .stream()
                                                       .map(candleGroupRequest -> createCandleGroupEntity(
                                                               instrumentEntity, candleGroupRequest))
                                                       .collect(toList());

        instrumentEntity.setCandleGroups(groupEntities);
        return instrumentEntity;
    }
}
