package com.example.tradingbot.domain.service.candlegroup.repair;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.domain.service.candlegroup.CandleGroupLeaseService;
import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
import com.example.tradingbot.domain.service.candles.okx.ClientCandle;
import com.example.tradingbot.domain.service.candles.okx.OkxCandleFetcher;
import com.example.tradingbot.persistence.model.CandleEntity;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.service.CandleDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GapWindowDownloader {

    private static final int DEFAULT_BATCH_LIMIT = 300;

    private final OkxCandleFetcher okxCandleFetcher;
    private final CandleDataService candleDataService;
    private final InstrumentDataService instrumentDataService;
    private final CandleGroupLeaseService candleGroupLeaseService;
    private final CandleGroupsProperties candleGroupsProperties;

    public GapRepairResult repairWindow(CandleGroupEntity group, CandleGroupRunContext ctx, TimeWindow gap) {
        String instrumentName = instrumentDataService.findById(group.getInstrumentId())
            .map(instrument -> instrument.getName())
            .filter(name -> !name.isBlank())
            .orElseThrow(() -> new IllegalStateException("Candle group instrument name is missing, groupId=" + group.getId()));

        long cursor = gap.toTs() + ctx.tfMillis();
        int batchLimit = resolveBatchLimit();
        int batchesProcessed = 0;
        int leaseExtendEveryBatches = resolveLeaseExtendEveryBatches();

        while (cursor >= gap.fromTs()) {
            List<ClientCandle> batch = okxCandleFetcher.fetchHistoryBackward(
                instrumentName,
                group.getTimeframe(),
                batchLimit,
                cursor
            );
            batchesProcessed++;

            if (batch.isEmpty()) {
                log.warn("CandleGroup repair stopped by empty batch: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, gapStart={}, gapEnd={}, cursor={}, batchesProcessed={}",
                    group.getId(), group.getInstrumentId(), group.getTimeframe(), group.getStatus(), ctx.nowClosedTs(), gap.fromTs(), gap.toTs(), cursor, batchesProcessed);
                break;
            }

            long minTs = batch.stream()
                .mapToLong(ClientCandle::getTimestampMillis)
                .min()
                .orElse(cursor);

            List<CandleEntity> mapped = batch.stream()
                .filter(candle -> candle.getTimestampMillis() >= gap.fromTs())
                .filter(candle -> candle.getTimestampMillis() <= gap.toTs())
                .filter(candle -> candle.getTimestampMillis() % ctx.tfMillis() == 0)
                .map(candle -> mapToEntity(group, candle))
                .toList();

            candleDataService.upsertBatch(group.getId(), mapped);

            if (minTs >= cursor) {
                log.warn("CandleGroup repair stopped by monotonic guard: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, gapStart={}, gapEnd={}, cursor={}, minTs={}, batchesProcessed={}",
                    group.getId(), group.getInstrumentId(), group.getTimeframe(), group.getStatus(), ctx.nowClosedTs(), gap.fromTs(), gap.toTs(), cursor, minTs, batchesProcessed);
                break;
            }

            long previousCursor = cursor;
            cursor = minTs;
            if (batchesProcessed % leaseExtendEveryBatches == 0) {
                candleGroupLeaseService.extendLease(group.getId());
            }

            log.info("CandleGroup repair batch: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, gapStart={}, gapEnd={}, previousCursor={}, nextCursor={}, fetchedBatch={}, savedBatch={}, batchesProcessed={}",
                group.getId(),
                group.getInstrumentId(),
                group.getTimeframe(),
                group.getStatus(),
                ctx.nowClosedTs(),
                gap.fromTs(),
                gap.toTs(),
                previousCursor,
                cursor,
                batch.size(),
                mapped.size(),
                batchesProcessed);
        }

        long expectedWindow = ((gap.toTs() - gap.fromTs()) / ctx.tfMillis()) + 1;
        long actualWindow = candleDataService.countBetween(group.getId(), gap.fromTs(), gap.toTs());
        return new GapRepairResult(gap, expectedWindow, actualWindow, actualWindow == expectedWindow);
    }

    private int resolveBatchLimit() {
        int configured = candleGroupsProperties.getBatchLimit();
        return configured > 0 ? configured : DEFAULT_BATCH_LIMIT;
    }

    private int resolveLeaseExtendEveryBatches() {
        int configured = candleGroupsProperties.getLeaseExtendEveryBatches();
        return configured > 0 ? configured : 1;
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
