package com.example.tradingbot.domain.service.ops;

import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.model.CandleGroupStatus;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InstrumentDataReadinessService {

    private static final String STATUS_SYNC = "SYNC";
    private static final String STATUS_HOLD = "HOLD";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANDLES_LOADING = "CANDLES_LOADING";

    private final InstrumentDataService instrumentDataService;
    private final CandleGroupDataService candleGroupDataService;

    @Transactional
    public void recomputeInstrumentStatusFromCandleGroups(Long instrumentId) {
        InstrumentEntity instrument = instrumentDataService.findById(instrumentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));

        if (Objects.equals(instrument.getStatus(), STATUS_SYNC) || Objects.equals(instrument.getStatus(), STATUS_HOLD)) {
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
            return STATUS_CANDLES_LOADING;
        }

        boolean allGroupsSynced = candleGroups.stream()
            .map(CandleGroupEntity::getStatus)
            .allMatch(status -> status == CandleGroupStatus.SYNC);

        if (allGroupsSynced) {
            return STATUS_ACTIVE;
        }
        return STATUS_CANDLES_LOADING;
    }
}
