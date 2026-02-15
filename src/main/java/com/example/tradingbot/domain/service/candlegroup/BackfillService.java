package com.example.tradingbot.domain.service.candlegroup;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
import com.example.tradingbot.domain.service.candles.okx.ClientCandle;
import com.example.tradingbot.domain.service.candles.okx.OkxCandleFetcher;
import com.example.tradingbot.persistence.model.CandleEntity;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.service.CandleDataService;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillService {

    private static final int DEFAULT_BATCH_LIMIT = 300;

    private final OkxCandleFetcher okxCandleFetcher;
    private final CandleDataService candleDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentDataService instrumentDataService;
    private final CandleGroupLeaseService candleGroupLeaseService;
    private final CandleGroupsProperties candleGroupsProperties;

    public BackfillResult backfillToCoverage(CandleGroupEntity group, CandleGroupRunContext context) {
        String instrumentName = instrumentDataService.findById(group.getInstrumentId())
            .map(instrument -> instrument.getName())
            .filter(name -> !name.isBlank())
            .orElseThrow(() -> new IllegalStateException("Candle group instrument name is missing, groupId=" + group.getId()));

        long cursor = initCursorIfNeeded(group, context);
        long coverageStartTs = group.getCoverageStartTs();

        int totalFetched = 0;
        int totalSaved = 0;
        int batchLimit = resolveBatchLimit();

        while (cursor > coverageStartTs) {
            List<ClientCandle> batch = okxCandleFetcher.fetchHistoryBackward(
                instrumentName,
                group.getTimeframe(),
                batchLimit,
                cursor
            );
            totalFetched += batch.size();

            if (batch.isEmpty()) {
                log.warn("CandleGroup backfill stopped by empty batch: groupId={}, cursor={}, coverageStartTs={}",
                    group.getId(), cursor, coverageStartTs);
                break;
            }

            long minTs = batch.stream()
                .mapToLong(ClientCandle::getTimestampMillis)
                .min()
                .orElse(cursor);

            List<CandleEntity> mappedCandles = batch.stream()
                .filter(candle -> candle.getTimestampMillis() >= coverageStartTs)
                .filter(candle -> candle.getTimestampMillis() <= context.nowClosedTs())
                .filter(candle -> candle.getTimestampMillis() % context.tfMillis() == 0)
                .map(candle -> mapToEntity(group, candle))
                .toList();

            totalSaved += candleDataService.upsertBatch(group.getId(), mappedCandles);

            if (minTs >= cursor) {
                log.warn("CandleGroup backfill stopped by monotonic guard: groupId={}, cursor={}, minTs={}",
                    group.getId(), cursor, minTs);
                break;
            }

            cursor = minTs;
            candleGroupDataService.updateBackfillCursor(group.getId(), cursor);
            group.setBackfillCursorTs(cursor);
            candleGroupLeaseService.extendLease(group.getId());
        }

        boolean completed = cursor <= coverageStartTs;
        return new BackfillResult(completed, cursor, totalFetched, totalSaved);
    }

    private long initCursorIfNeeded(CandleGroupEntity group, CandleGroupRunContext context) {
        if (group.getBackfillCursorTs() != null) {
            return group.getBackfillCursorTs();
        }

        long initializedCursor = context.nowClosedTs() + context.tfMillis();
        candleGroupDataService.updateBackfillCursor(group.getId(), initializedCursor);
        group.setBackfillCursorTs(initializedCursor);
        log.info("CandleGroup backfill cursor initialized: groupId={}, cursor={}, nowClosedTs={}, tfMillis={}",
            group.getId(), initializedCursor, context.nowClosedTs(), context.tfMillis());
        return initializedCursor;
    }

    private int resolveBatchLimit() {
        int configured = candleGroupsProperties.getBatchLimit();
        return configured > 0 ? configured : DEFAULT_BATCH_LIMIT;
    }

    private CandleEntity mapToEntity(CandleGroupEntity group, ClientCandle source) {
        CandleEntity candle = new CandleEntity();
        candle.setCandleGroup(group);
        candle.setTimestamp(source.getTimestampMillis());
        candle.setOpen(source.getOpen());
        candle.setHigh(source.getHigh());
        candle.setLow(source.getLow());
        candle.setClose(source.getClose());
        candle.setVolume(source.getVolume());
        return candle;
    }
}
