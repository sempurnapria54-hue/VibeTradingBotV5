package com.example.tradingbot.util.factory;

import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.rest.model.request.CreateCandleGroupRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CandleGroupFactory {

    public static CandleGroupEntity createCandleGroup(InstrumentEntity instrument, CreateCandleGroupRequest request) {
        var candleGroupEntity = new CandleGroupEntity();
        candleGroupEntity.initOnCreate(instrument, request);
        return candleGroupEntity;
    }
}
