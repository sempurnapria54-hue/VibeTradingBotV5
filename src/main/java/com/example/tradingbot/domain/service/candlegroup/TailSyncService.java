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
public class TailSyncService {

    static final int DEFAULT_TAIL_OVERLAP_BARS = 300;

    private final OkxCandleFetcher okxCandleFetcher;
    private final CandleDataService candleDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentDataService instrumentDataService;
    private final CandleGroupsProperties candleGroupsProperties;

    public TailSyncResult syncTail(CandleGroupEntity group, CandleGroupRunContext context) {
        String instrumentName = instrumentDataService.findById(group.getInstrumentId())
            .map(instrument -> instrument.getName())
            .filter(name -> !name.isBlank())
            .orElseThrow(() -> new IllegalStateException("Candle group instrument name is missing, groupId=" + group.getId()));

        int overlapBars = resolveOverlapBars(group.getTimeframe());
        List<ClientCandle> fetchedCandles = okxCandleFetcher.fetchTail(instrumentName, group.getTimeframe(), overlapBars);

        List<CandleEntity> mappedCandles = fetchedCandles.stream()
            .filter(candle -> candle.getTimestampMillis() <= context.nowClosedTs())
            .filter(candle -> candle.getTimestampMillis() % context.tfMillis() == 0)
            .map(candle -> mapToEntity(group, candle))
            .toList();

        int savedCandles = candleDataService.upsertBatch(group.getId(), mappedCandles);
        candleGroupDataService.updateLastTailSync(group.getId(), context.nowClosedTs());

        TailSyncResult result = new TailSyncResult(fetchedCandles.size(), savedCandles, context.nowClosedTs());
        log.info("CandleGroup tail sync completed: groupId={}, timeframe={}, overlapBars={}, fetched={}, saved={}, updatedLastTailSyncTs={}",
            group.getId(),
            group.getTimeframe(),
            overlapBars,
            result.fetched(),
            result.saved(),
            result.updatedLastTailSyncTs());
        return result;
    }

    private int resolveOverlapBars(String timeframe) {
        Integer configured = candleGroupsProperties.getTailOverlapBars().get(timeframe);
        if (configured == null || configured <= 0) {
            return DEFAULT_TAIL_OVERLAP_BARS;
        }
        return configured;
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
