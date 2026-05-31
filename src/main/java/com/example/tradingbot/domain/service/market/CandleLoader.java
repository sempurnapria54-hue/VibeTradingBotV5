package com.example.tradingbot.domain.service.market;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.config.CandleLoadingProperties;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.persistence.service.CandleDataService;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

/**
 * Ведёт одну {@link CandleGroup} по жизненному циклу загрузки свечей
 * (docs/lifecycles/CandleGroup.md): BACKFILL (выкачка истории «в
 * глубину» до планового горизонта/пустого ответа) → SYNC (докачка
 * хвоста) → CHECK (проверка целостности по count) → REPAIR (докачка дыр
 * бинарным поиском) → ACTIVE. Идемпотентность — на уровне
 * CandleDataService (уникальность (group, openTimestamp)). В историю
 * идут только закрытые свечи (confirm=1).
 *
 * <p>Счётчик попыток REPAIR держится в памяти (на доменной модели поля
 * нет — концепция его не вводит); при рестарте сбрасывается. Флаг под
 * ревью/SYNC_DOCS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleLoader {

    private final ClientService clientService;
    private final CandleDataService candleDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentDataService instrumentDataService;
    private final CandleMapper candleMapper;
    private final CandleLoadingProperties properties;

    private final Map<Long, Integer> repairAttempts = new ConcurrentHashMap<>();

    /** Продвигает группу на один шаг согласно её статусу. */
    public void advance(CandleGroup group) {
        switch (group.getStatus()) {
            case CREATED -> startBackfill(group);
            case BACKFILL -> backfill(group);
            case SYNC -> sync(group);
            case CHECK -> check(group);
            case REPAIR -> repair(group);
            default -> {
                // ACTIVE / ERROR / DELETED — в этом цикле не ведутся.
            }
        }
    }

    private void startBackfill(CandleGroup group) {
        group.setStatus(CandleGroup.Status.BACKFILL);
        candleGroupDataService.save(group);
    }

    private void backfill(CandleGroup group) {
        Instrument instrument = requireInstrument(group);
        List<CandleExternalSnapshot> page = clientService.getHistoryCandles(
                instrument.getExternalId(), group.getExternalTimeframe(),
                group.getActualFirstUtcMillis(), properties.getPageSize());
        persistClosed(group, page);
        reconcile(group);
        if (isBackfillComplete(group, instrument, page)) {
            group.setStatus(CandleGroup.Status.CHECK);
        }
        candleGroupDataService.save(group);
    }

    private void sync(CandleGroup group) {
        Instrument instrument = requireInstrument(group);
        List<CandleExternalSnapshot> page = clientService.getLatestCandles(
                instrument.getExternalId(), group.getExternalTimeframe(), properties.getPageSize());
        persistClosed(group, page);
        reconcile(group);
        group.setStatus(CandleGroup.Status.CHECK);
        candleGroupDataService.save(group);
    }

    private void check(CandleGroup group) {
        reconcile(group);
        if (group.isDense()) {
            repairAttempts.remove(group.getId());
            group.setStatus(CandleGroup.Status.ACTIVE);
        } else {
            group.setStatus(CandleGroup.Status.REPAIR);
        }
        candleGroupDataService.save(group);
    }

    private void repair(CandleGroup group) {
        int attempts = repairAttempts.merge(group.getId(), 1, Integer::sum);
        if (attempts > properties.getMaxRepairAttempts()) {
            log.error("CandleGroup {} exceeded {} repair attempts -> ERROR",
                    group.getId(), properties.getMaxRepairAttempts());
            repairAttempts.remove(group.getId());
            group.setStatus(CandleGroup.Status.ERROR);
            candleGroupDataService.save(group);
            return;
        }
        HoleWindow window = locateHole(group);
        if (Objects.isNull(window)) {
            group.setStatus(CandleGroup.Status.CHECK);
            candleGroupDataService.save(group);
            return;
        }
        Instrument instrument = requireInstrument(group);
        long step = group.getTimeframe().getDurationMillis();
        List<CandleExternalSnapshot> page = clientService.getHistoryCandles(
                instrument.getExternalId(), group.getExternalTimeframe(),
                window.toMillis() + step, properties.getPageSize());
        persistClosed(group, page);
        reconcile(group);
        group.setStatus(CandleGroup.Status.CHECK);
        candleGroupDataService.save(group);
    }

    private boolean isBackfillComplete(CandleGroup group, Instrument instrument, List<CandleExternalSnapshot> page) {
        if (CollectionUtils.isEmpty(page)) {
            return true;
        }
        Long first = group.getActualFirstUtcMillis();
        Long horizon = instrument.getPlannedCandleStartDate();
        return Objects.nonNull(first) && Objects.nonNull(horizon) && first <= horizon;
    }

    /**
     * Локализует окно с дырой бинарным поиском по count: делит
     * [actualFirst, actualLast], в дефицитную половину спускается, пока
     * окно не сузится до ≤ pageSize баров.
     */
    private HoleWindow locateHole(CandleGroup group) {
        Long first = group.getActualFirstUtcMillis();
        Long last = group.getActualLastUtcMillis();
        if (Objects.isNull(first) || Objects.isNull(last)) {
            return null;
        }
        long step = group.getTimeframe().getDurationMillis();
        long windowSpan = step * properties.getPageSize();
        long lo = first;
        long hi = last;
        while (hi - lo > windowSpan) {
            long mid = lo + alignDown((hi - lo) / 2, step);
            long expectedLeft = (mid - lo) / step + 1L;
            long actualLeft = candleDataService.countInRange(group.getId(), lo, mid);
            if (actualLeft < expectedLeft) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return new HoleWindow(lo, hi);
    }

    private void persistClosed(CandleGroup group, List<CandleExternalSnapshot> snapshots) {
        List<Candle> closed = snapshots.stream()
                .filter(CandleExternalSnapshot::isConfirm)
                .map(candleMapper::snapshotToDomain)
                .toList();
        candleDataService.saveNewCandles(group.getId(), closed);
    }

    private void reconcile(CandleGroup group) {
        Long groupId = group.getId();
        group.setCount(candleDataService.count(groupId));
        group.setActualFirstUtcMillis(candleDataService.findMinOpenTimestamp(groupId));
        group.setActualLastUtcMillis(candleDataService.findMaxOpenTimestamp(groupId));
    }

    private Instrument requireInstrument(CandleGroup group) {
        return instrumentDataService.findById(group.getInstrumentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Instrument not found for candle group: " + group.getId()));
    }

    private long alignDown(long value, long step) {
        return (value / step) * step;
    }

    private record HoleWindow(long fromMillis, long toMillis) {
    }
}
