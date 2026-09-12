package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.domain.model.FeatureBinding;
import com.example.marketdata.domain.model.FeatureReadRequest;
import com.example.marketdata.domain.model.MarketFeatureBundle;
import com.example.marketdata.integration.internal.api.ExchangeReadException;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Собирает фичи на момент решения: значения по привязкам читателя, цену
 * момента и — если читатель передал клаузы — классифицированную фазу.
 * См. docs/architecture/market-data-collection.md §Производные,
 * docs/architecture/contracts.md §«Синхронные вызовы».
 *
 * <p><b>Чтение одно на момент решения, а не по операнду.</b> Потребителю
 * нужны сразу все входы своих условий и своих калькуляторов, и снимать их
 * россыпью значило бы, во-первых, платить круговым обходом за каждое имя,
 * во-вторых — собрать контекст из значений РАЗНЫХ моментов: пока идут
 * пятнадцать вызовов, тик расчёта успевает записать новое значение, и
 * условие сравнило бы величины, не существовавшие одновременно.
 *
 * <p><b>Предыдущее значение отдаётся вместе с последним.</b> Правила
 * пересечения и объёмный фильтр сравнивают текущее значение с прошлым, и
 * без второй половины интерпретатор возвращает ЛОЖЬ — шаг стратегии не
 * исполняется молча (docs/components/StrategyConditionEvaluator.md).
 * Прежде поверхность отдавала только последнее, и предыдущее читала
 * одна лишь собственная классификация фазы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketFeatureService {

    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;
    private final MarketPriceDataService marketPriceDataService;
    private final MarketPhaseService marketPhaseService;

    /** Фичи одного момента по привязкам и клаузам читателя. */
    public MarketFeatureBundle readFeatures(Instrument instrument, FeatureReadRequest request) {
        Map<String, IndicatorValue> latestIndicators = new HashMap<>();
        Map<String, IndicatorValue> previousIndicators = new HashMap<>();
        Long instrumentId = instrument.getId();
        for (FeatureBinding binding : emptyIfNull(request.getIndicatorBindings())) {
            indicatorService.getLatestValue(instrumentId, binding.getConfigId(), binding.getTolerance())
                    .ifPresent(value -> latestIndicators.put(binding.getKey(), value));
            indicatorService.getPreviousValue(instrumentId, binding.getConfigId())
                    .ifPresent(value -> previousIndicators.put(binding.getKey(), value));
        }
        Map<String, MarketStructure> structures = new HashMap<>();
        for (FeatureBinding binding : emptyIfNull(request.getStructureBindings())) {
            marketStructureService.getLatestStructure(instrumentId, binding.getConfigId(), binding.getTolerance())
                    .ifPresent(structure -> structures.put(binding.getKey(), structure));
        }
        MarketPriceData priceData = resolvePrices(instrument, request);
        ConditionEvaluationContext context = ConditionEvaluationContext.builder()
                .latestIndicators(latestIndicators)
                .previousIndicators(previousIndicators)
                .structures(structures)
                .price(isNull(priceData) ? null : priceData.getExternalLastPrice())
                .evaluationTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        return MarketFeatureBundle.builder()
                .latestIndicators(latestIndicators)
                .previousIndicators(previousIndicators)
                .structures(structures)
                .marketPriceData(priceData)
                .marketPhase(marketPhaseService.resolve(instrumentId, request.getPhaseRules(), context)
                        .orElse(null))
                .build();
    }

    /**
     * Цены момента; пусто — читатель их не спрашивал либо площадка
     * отказала.
     *
     * <p><b>Чтение у площадки идёт, только если цену спрашивают.</b>
     * Индикаторы и структуры читаются из своего хранилища, а цена —
     * round-trip наружу через коннектор: делать его там, где
     * {@code PRICE}-операнда нет, значит платить задержкой и доступностью
     * площадки за вход, который никто не спросил.
     *
     * <p><b>Недоступная цена даёт ПУСТОЙ операнд, а не отказ чтения.</b>
     * Семантика входа одна для всех: отсутствующий в контекст не попадает,
     * операнд недоступен, предикат на нём консервативно ложен, а фаза —
     * {@code UNKNOWN}. Для индикаторов и структур это держалось само (их
     * чтение отдаёт пустоту), а у цены отказ площадки уходил исключением
     * наружу — и потребитель получал отказ там, где по контракту ему
     * полагалась пустота.
     */
    private MarketPriceData resolvePrices(Instrument instrument, FeatureReadRequest request) {
        if (isFalse(request.usesPriceOperand())) {
            return null;
        }
        try {
            return marketPriceDataService.getMarketPriceData(instrument.getId(), instrument.getExternalId());
        } catch (ExchangeReadException e) {
            log.warn("Price operand unavailable for instrument {}: feature read returns it empty",
                    instrument.getId(), e);
            return null;
        }
    }
}
