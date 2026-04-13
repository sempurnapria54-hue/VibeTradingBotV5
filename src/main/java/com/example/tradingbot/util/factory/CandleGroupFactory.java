package com.example.tradingbot.util.factory;

import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import com.example.tradingbot.rest.model.request.candle_group.CreateCandleGroupRequest;
import lombok.experimental.UtilityClass;

import static com.example.tradingbot.util.Constant.Status.CandleGroup.CANDLE_GROUP_STATUS_CREATED;

@UtilityClass
public class CandleGroupFactory {

    public static CandleGroupEntity createCandleGroupEntity(InstrumentEntity instrument,
                                                            CreateCandleGroupRequest request) {
        var candleGroupEntity = new CandleGroupEntity();
        candleGroupEntity.setTimeframe(request.getTimeframe());
        candleGroupEntity.setStatus(CANDLE_GROUP_STATUS_CREATED);
        candleGroupEntity.setInstrument(instrument);
        return candleGroupEntity;
    }
}
