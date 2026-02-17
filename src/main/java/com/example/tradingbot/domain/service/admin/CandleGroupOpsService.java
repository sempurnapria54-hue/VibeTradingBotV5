package com.example.tradingbot.domain.service.admin;

import com.example.tradingbot.domain.model.admin.CandleGroupBootstrapRequest;
import com.example.tradingbot.domain.model.admin.CandleGroupView;
import com.example.tradingbot.domain.service.candlegroup.CandleGroupLeaseService;
import com.example.tradingbot.domain.service.candlegroup.CandleGroupWorker;
import com.example.tradingbot.domain.service.ops.InstrumentDataReadinessService;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.model.CandleGroupStatus;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.util.OkxTimeframes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CandleGroupOpsService {

    private final InstrumentDataService instrumentDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final CandleGroupLeaseService candleGroupLeaseService;
    private final CandleGroupWorker candleGroupWorker;
    private final InstrumentDataReadinessService instrumentDataReadinessService;

    @Transactional
    public List<CandleGroupView> bootstrap(Long instrumentId, CandleGroupBootstrapRequest request) {
        InstrumentEntity instrument = instrumentDataService.findById(instrumentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));

        validateBootstrapRequest(request);

        Set<String> uniqueTimeframes = new LinkedHashSet<>(request.getTimeframes());
        List<CandleGroupView> createdGroups = new ArrayList<>();

        for (String timeframe : uniqueTimeframes) {
            if (candleGroupDataService.findByInstrumentIdAndTimeframe(instrumentId, timeframe).isPresent()) {
                continue;
            }

            CandleGroupEntity candleGroupEntity = new CandleGroupEntity();
            candleGroupEntity.setInstrument(instrument);
            candleGroupEntity.setTimeframe(timeframe);
            candleGroupEntity.setStatus(CandleGroupStatus.NEW);
            candleGroupEntity.setCoverageStartTs(request.getCoverageStartTs());
            candleGroupEntity.setAttemptCount(0);

            CandleGroupEntity created = candleGroupDataService.create(candleGroupEntity);
            createdGroups.add(toView(created));
        }

        instrumentDataReadinessService.recomputeInstrumentStatusFromCandleGroups(instrumentId);

        return createdGroups;
    }

    public List<CandleGroupView> listByInstrument(Long instrumentId) {
        if (BooleanUtils.isFalse(instrumentDataService.existsById(instrumentId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found");
        }

        return candleGroupDataService.findAllByInstrumentId(instrumentId)
            .stream()
            .map(this::toView)
            .toList();
    }

    public void runOnce(Long groupId) {
        CandleGroupEntity group = candleGroupDataService.getById(groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candle group not found"));

        boolean acquired = candleGroupLeaseService.acquireLease(group.getId());
        if (BooleanUtils.isFalse(acquired)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candle group lease is already acquired");
        }

        try {
            candleGroupWorker.processGroup(group.getId());
        } finally {
            candleGroupLeaseService.releaseLease(group.getId());
        }
    }

    private void validateBootstrapRequest(CandleGroupBootstrapRequest request) {
        if (Objects.isNull(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        if (Objects.isNull(request.getCoverageStartTs())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coverageStartTs is required");
        }

        List<String> timeframes = request.getTimeframes();
        if (Objects.isNull(timeframes) || timeframes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeframes must not be empty");
        }

        for (String timeframe : timeframes) {
            if (BooleanUtils.isFalse(OkxTimeframes.isSupported(timeframe))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported timeframe: " + timeframe);
            }
        }
    }

    private CandleGroupView toView(CandleGroupEntity source) {
        return new CandleGroupView(
            source.getId(),
            source.getInstrumentId(),
            source.getTimeframe(),
            source.getStatus(),
            source.getCoverageStartTs(),
            source.getBackfillCursorTs(),
            source.getLastTailSyncTs(),
            source.getAttemptCount(),
            source.getLeaseOwner(),
            source.getLeaseUntil()
        );
    }
}
