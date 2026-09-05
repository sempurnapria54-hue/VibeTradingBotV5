package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.marketdata.config.CandleLoadingProperties;
import com.example.marketdata.integration.ExchangeReadClient;
import com.example.marketdata.persistence.service.CandleDataService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ведёт одну {@link CandleGroup} по жизненному циклу загрузки свечей
 * (docs/lifecycles/CandleGroup.md): BACKFILL (выкачка истории в глубину
 * до заказанного горизонта либо пустого ответа) → SYNC (докачка хвоста) →
 * CHECK (проверка целостности по count) → REPAIR (докачка дыр бинарным
 * поиском) → ACTIVE. Идемпотентность держит {@link CandleDataService}
 * (естественный ключ группы и открытия бара).
 *
 * <p><b>Горизонт бэкфилла берётся у ГРУППЫ, а не у инструмента:</b>
 * глубину называет требование потребителя, и у 1m и 1D одного
 * инструмента она разная (docs/processes/candle-loading.md §«Кто заводит
 * группу»).
 *
 * <p><b>Незакрытые бары сюда не доходят:</b> признак закрытия виден
 * коннектору, и наружу он отдаёт только закрытые
 * (docs/models/domain/other/Candle.md). Второй фильтр по признаку,
 * которого в ответе нет, был бы фикцией.
 *
 * <p>Счётчик попыток докачки держится в памяти: на доменной модели поля
 * нет, и при рестарте счётчик обнуляется — цена названа, и она меньше,
 * чем колонка состояния, которую никто, кроме этого цикла, не читает.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleLoader {

    private final ExchangeReadClient readClient;
    private final CandleDataService candleDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentDataService instrumentDataService;
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
        Instrument instrument = instrumentDataService.getRequiredById(group.getInstrumentId());
        List<Candle> page = readClient.getHistoryCandles(instrument.getExternalId(), group.getTimeframe(),
                group.getActualFirstUtcMillis(), properties.getPageSize());
        persist(group, page);
        reconcile(group);
        if (isBackfillComplete(group, page)) {
            group.setStatus(CandleGroup.Status.CHECK);
        }
        candleGroupDataService.save(group);
    }

    private void sync(CandleGroup group) {
        Instrument instrument = instrumentDataService.getRequiredById(group.getInstrumentId());
        List<Candle> page = readClient.getLatestCandles(instrument.getExternalId(), group.getTimeframe(),
                properties.getPageSize());
        persist(group, page);
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
        if (isNull(window)) {
            group.setStatus(CandleGroup.Status.CHECK);
            candleGroupDataService.save(group);
            return;
        }
        Instrument instrument = instrumentDataService.getRequiredById(group.getInstrumentId());
        long step = group.getTimeframe().getDurationMillis();
        List<Candle> page = readClient.getHistoryCandles(instrument.getExternalId(), group.getTimeframe(),
                window.toMillis() + step, properties.getPageSize());
        persist(group, page);
        reconcile(group);
        group.setStatus(CandleGroup.Status.CHECK);
        candleGroupDataService.save(group);
    }

    /**
     * Бэкфилл закончен, когда площадка отдала пусто (начало её истории)
     * либо нижняя граница загруженного достигла заказанного горизонта.
     *
     * <p>Пустой горизонт означает «глубина не заказана», и тогда бэкфилл
     * идёт до конца истории площадки: требование без глубины — требование
     * всей доступной истории, а не отсутствие требования.
     */
    private boolean isBackfillComplete(CandleGroup group, List<Candle> page) {
        if (isEmpty(page)) {
            return true;
        }
        Long first = group.getActualFirstUtcMillis();
        Long horizon = group.getPlannedFirstUtcMillis();
        return nonNull(first) && nonNull(horizon) && first <= horizon;
    }

    /**
     * Локализует окно с дырой бинарным поиском по count: делит
     * [actualFirst, actualLast], в дефицитную половину спускается, пока
     * окно не сузится до размера страницы.
     */
    private HoleWindow locateHole(CandleGroup group) {
        Long first = group.getActualFirstUtcMillis();
        Long last = group.getActualLastUtcMillis();
        if (isNull(first) || isNull(last)) {
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

    private void persist(CandleGroup group, List<Candle> candles) {
        candleDataService.saveCandles(group.getId(), candles);
    }

    private void reconcile(CandleGroup group) {
        Long groupId = group.getId();
        group.setCount(candleDataService.count(groupId));
        group.setActualFirstUtcMillis(candleDataService.findMinOpenTimestamp(groupId));
        group.setActualLastUtcMillis(candleDataService.findMaxOpenTimestamp(groupId));
    }

    private long alignDown(long value, long step) {
        return (value / step) * step;
    }
}
