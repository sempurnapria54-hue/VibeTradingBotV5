package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CandleGroupFactory {

    public static CandleGroupEntity createCandleGroup(InstrumentEntity instrument, String timeFrame) {
        var candleGroupEntity = new CandleGroupEntity();
        candleGroupEntity.initOnCreate(instrument);
        candleGroupEntity.setTimeframe(timeFrame);
        return candleGroupEntity;
    }
}
