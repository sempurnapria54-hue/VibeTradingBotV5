package com.example.tradingbot.domain.service.ops;

import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.Constant.Status.CandleGroup.CANDLE_GROUP_STATUS_SYNC;
import static com.example.tradingbot.util.Constant.Status.Instrument.INSTRUMENT_STATUS_ACTIVE;
import static com.example.tradingbot.util.Constant.Status.Instrument.INSTRUMENT_STATUS_CANDLES_LOADING;
import static com.example.tradingbot.util.Constant.Status.Instrument.INSTRUMENT_STATUS_HOLD;
import static com.example.tradingbot.util.Constant.Status.Instrument.INSTRUMENT_STATUS_SYNC;

@Service
@RequiredArgsConstructor
public class InstrumentDataReadinessService {

    private final InstrumentDataService instrumentDataService;
    private final CandleGroupDataService candleGroupDataService;

    @Transactional
    public void recomputeInstrumentStatusFromCandleGroups(Long instrumentId) {
        InstrumentEntity instrument = instrumentDataService.findRequiredById(instrumentId);

        if (Objects.equals(instrument.getStatus(), INSTRUMENT_STATUS_SYNC) || Objects.equals(instrument.getStatus(), INSTRUMENT_STATUS_HOLD)) {
            return;
        }

        List<CandleGroupEntity> candleGroups = candleGroupDataService.findAllByInstrumentId(instrumentId);
        String targetStatus = determineTargetStatus(candleGroups);
        if (BooleanUtils.isFalse(Objects.equals(instrument.getStatus(), targetStatus))) {
            instrument.setStatus(targetStatus);
            instrumentDataService.save(instrument);
        }
    }

    private String determineTargetStatus(List<CandleGroupEntity> candleGroups) {
        if (candleGroups.isEmpty()) {
            return INSTRUMENT_STATUS_CANDLES_LOADING;
        }

        boolean allGroupsSynced = candleGroups.stream()
                .map(CandleGroupEntity::getStatus)
                .allMatch(status -> Objects.equals(status, CANDLE_GROUP_STATUS_SYNC));

        if (allGroupsSynced) {
            return INSTRUMENT_STATUS_ACTIVE;
        }
        return INSTRUMENT_STATUS_CANDLES_LOADING;
    }
}
