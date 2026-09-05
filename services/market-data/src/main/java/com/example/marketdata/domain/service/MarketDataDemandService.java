package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.domain.model.IndicatorConfig;
import com.example.marketdata.domain.model.MarketStructureConfig;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.ComputationConfigDataService;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.marketdata.util.Constants;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Принимает требования потребителей: что собирать и что считать.
 *
 * <p><b>Требование выражает потребитель синхронной командой</b> — не
 * событием и не конфигурацией
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»). Команда свечей называет инструмент, таймфрейм и глубину; из
 * них выводится единица сбора и горизонт бэкфилла.
 *
 * <p><b>Повтор безопасен по построению.</b> Требование того же на то же —
 * та же единица сбора, а не вторая; глубже прежнего — расширение
 * горизонта (docs/rules/idempotency-via-unique.md). Отзыва требования
 * нет: собранное остаётся, иначе снятие одной стратегии удаляло бы
 * историю, на которой стои́т бэктест другой.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataDemandService {

    private final InstrumentDataService instrumentDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final ComputationConfigDataService configDataService;

    /**
     * Требование свечей: заводит единицу сбора либо расширяет её горизонт.
     *
     * @param depthBars сколько баров истории нужно потребителю; пусто —
     *                  вся доступная история площадки.
     */
    public CandleGroup requireCandles(String instrumentInternalId, TimeFrame timeframe, Long depthBars) {
        Instrument instrument = instrumentDataService.getRequiredByInternalId(instrumentInternalId);
        Long horizon = resolveHorizon(timeframe, depthBars);
        Optional<CandleGroup> existing = candleGroupDataService
                .findByInstrumentIdAndTimeframe(instrument.getId(), timeframe);
        if (existing.isPresent()) {
            return deepenHorizon(existing.get(), horizon);
        }
        return candleGroupDataService.save(newGroup(instrument, timeframe, horizon));
    }

    /** Требование индикатора: заводит идентичность вычисления либо возвращает уже заведённую. */
    public IndicatorConfig requireIndicator(IndicatorConfig config) {
        return configDataService.ensureIndicatorConfig(config);
    }

    /** Требование структуры рынка: заводит идентичность вычисления либо возвращает уже заведённую. */
    public MarketStructureConfig requireMarketStructure(MarketStructureConfig config) {
        return configDataService.ensureMarketStructureConfig(config);
    }

    /**
     * Расширяет горизонт группы, если требование глубже уже стоящего.
     *
     * <p>Мельче стоящего — не сужение: собранное не выбрасывается, потому
     * что его заказал кто-то другой.
     *
     * <p><b>Углублённый горизонт возвращает к бэкфиллу группу в ЛЮБОМ
     * живом статусе, а не только готовую.</b> Дотягивание нижней границы
     * до планового горизонта — забота одного лишь {@code BACKFILL}
     * (docs/models/domain/other/CandleGroup.md §«Целостность по count»),
     * и остальные статусы цикла к нему сами не возвращаются: группа,
     * которую застали в {@code SYNC}/{@code CHECK}/{@code REPAIR}, дошла бы до
     * {@code ACTIVE} с непокрытым горизонтом, и требование потерялось бы
     * молча. Окно это не редкое: докачка хвоста уводит группу из
     * {@code ACTIVE} на каждом новом закрытом баре. Терминальные статусы
     * требованием не оживляются ({@code CandleGroup.isTerminal()}).
     */
    private CandleGroup deepenHorizon(CandleGroup group, Long horizon) {
        if (isNull(horizon)) {
            return group;
        }
        Long standing = group.getPlannedFirstUtcMillis();
        if (nonNull(standing) && standing <= horizon) {
            return group;
        }
        group.setPlannedFirstUtcMillis(horizon);
        if (isFalse(group.isTerminal())) {
            group.setStatus(CandleGroup.Status.BACKFILL);
        }
        return candleGroupDataService.save(group);
    }

    private CandleGroup newGroup(Instrument instrument, TimeFrame timeframe, Long horizon) {
        CandleGroup group = new CandleGroup();
        group.setInternalId(instrument.getInternalId() + Constants.InternalId.SEPARATOR + timeframe.name());
        group.setInstrumentId(instrument.getId());
        group.setTimeframe(timeframe);
        group.setStatus(CandleGroup.Status.CREATED);
        group.setPlannedFirstUtcMillis(horizon);
        group.setCount(0L);
        return group;
    }

    /**
     * Глубина в барах в нижнюю границу истории. Пустая глубина оставляет
     * горизонт пустым — это «вся доступная история», а не «истории не
     * нужно».
     */
    private Long resolveHorizon(TimeFrame timeframe, Long depthBars) {
        if (isNull(depthBars)) {
            return null;
        }
        return Instant.now().toEpochMilli() - depthBars * timeframe.getDurationMillis();
    }
}
