package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

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
import java.util.List;
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

    /**
     * Сколько раз приём требования перечитывает группу, переписанную шагом
     * цикла между чтением и записью. Шаг пишет группу раз за тик, поэтому
     * повтор, упёршийся в предел, означает не гонку, а отказ записи.
     */
    private static final int DEEPENING_ATTEMPTS = 3;

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

    /**
     * Требование индикатора: заводит идентичность вычисления либо возвращает
     * уже заведённую. Параметры, на которых вычисление не определено,
     * отвергаются здесь, а не у расчёта.
     */
    public IndicatorConfig requireIndicator(IndicatorConfig config) {
        rejectDefects(config.parameterDefects());
        return configDataService.ensureIndicatorConfig(config);
    }

    /**
     * Требование структуры рынка: заводит идентичность вычисления либо
     * возвращает уже заведённую. Параметры, на которых вычисление не
     * определено, отвергаются здесь, а не у расчёта.
     */
    public MarketStructureConfig requireMarketStructure(MarketStructureConfig config) {
        rejectDefects(config.parameterDefects());
        return configDataService.ensureMarketStructureConfig(config);
    }

    /**
     * Отказ создания идентичности — классом негодного входа: вызывающий
     * прислал то, что не посчитается никогда, и узнать это он обязан сейчас,
     * а не пустотой на чтении фич (docs/architecture/market-data-collection.md
     * §«Как потребность доходит до сбора»).
     */
    private void rejectDefects(List<String> defects) {
        if (isNotEmpty(defects)) {
            throw new IllegalArgumentException("Computation params are not computable: "
                    + String.join("; ", defects));
        }
    }

    /**
     * Расширяет горизонт группы, если требование глубже уже стоящего.
     *
     * <p>Мельче стоящего — не сужение: собранное не выбрасывается, потому
     * что его заказал кто-то другой (docs/models/domain/other/CandleGroup.md
     * §«Горизонт бэкфилла принадлежит группе»).
     *
     * <p><b>Углублённый горизонт возвращает к бэкфиллу группу в ЛЮБОМ
     * живом статусе, а не только готовую</b>; терминальные статусы
     * требованием не оживляются, а горизонт у них всё равно расширяется
     * (docs/lifecycles/CandleGroup.md §«Возврат к `BACKFILL` по углублённому
     * требованию»).
     *
     * <p><b>Пишутся только горизонт и статус — точечной записью под гардом
     * застанного.</b> Шаг цикла загрузки пишет ту же строку в любой момент,
     * и запись группы целиком вернула бы его итог к снимку, прочитанному
     * требованием: группа в {@code ERROR} оживала бы как {@code BACKFILL},
     * счёт и границы устаревали бы до следующего шага. Шаг, успевший
     * записать итог между чтением и записью, роняет гард; тогда группа
     * перечитывается, и решение принимается заново по тому, что шаг
     * записал.
     */
    private CandleGroup deepenHorizon(CandleGroup loaded, Long horizon) {
        CandleGroup group = loaded;
        for (int attempt = 0; attempt < DEEPENING_ATTEMPTS; attempt++) {
            if (isFalse(isDeeper(horizon, group.getPlannedFirstUtcMillis()))) {
                return group;
            }
            CandleGroup.Status loadedStatus = group.getStatus();
            Long loadedHorizon = group.getPlannedFirstUtcMillis();
            group.setPlannedFirstUtcMillis(horizon);
            if (isFalse(group.isTerminal())) {
                group.setStatus(CandleGroup.Status.BACKFILL);
            }
            if (isTrue(candleGroupDataService.saveDeepenedHorizon(group, loadedStatus, loadedHorizon))) {
                return group;
            }
            log.info("CandleGroup {} was rewritten while a deeper requirement was being accepted; re-reading",
                    group.getId());
            group = candleGroupDataService.getRequiredById(group.getId());
        }
        throw new IllegalStateException("CandleGroup " + loaded.getId()
                + " kept being rewritten while a deeper requirement was being accepted");
    }

    /**
     * Глубже ли требуемый горизонт стоящего. Пустой горизонт — вся история
     * площадки (docs/lifecycles/CandleGroup.md §«Глубина и покрытие
     * (`BACKFILL`)»), и глубже него не бывает ничего; требование всей
     * истории глубже всякого названного горизонта.
     */
    private Boolean isDeeper(Long requested, Long standing) {
        if (isNull(standing)) {
            return false;
        }
        return isNull(requested) || requested < standing;
    }

    private CandleGroup newGroup(Instrument instrument, TimeFrame timeframe, Long horizon) {
        CandleGroup group = new CandleGroup();
        group.setInternalId(instrument.getInternalId() + Constants.InternalId.SEPARATOR + timeframe.name());
        group.setInstrumentId(instrument.getId());
        group.setTimeframe(timeframe);
        group.setStatus(CandleGroup.Status.CREATED);
        group.setPlannedFirstUtcMillis(horizon);
        group.setCount(0L);
        group.resetRepairAttempts();
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
