package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.domain.model.FeatureBinding;
import com.example.marketdata.domain.model.FeatureReadRequest;
import com.example.marketdata.domain.model.MarketFeatureBundle;
import com.example.marketdata.exception.ExchangeReadException;
import com.example.marketdata.persistence.service.CandleDataService;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
 *
 * <p><b>Прошлое цены отдаётся по ключу индикатора, а не одним скаляром.</b>
 * Пересечение цены с индикатором сравнивает и прошлую половину: предыдущее
 * значение индикатора против закрытия свечи, на которой оно посчитано. У
 * цены своего таймфрейма нет, момент задаёт серия индикатора, и один скаляр
 * на условие с несколькими таймфреймами был бы прошлым не той свечи
 * (docs/components/StrategyConditionEvaluator.md §«Вторая половина
 * времени»). Свеча читается из своего ряда, наружу чтение не ходит.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketFeatureService {

    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;
    private final MarketPriceDataService marketPriceDataService;
    private final MarketPhaseService marketPhaseService;
    private final CandleDataService candleDataService;

    /** Фичи одного момента по привязкам и клаузам читателя. */
    public MarketFeatureBundle readFeatures(Instrument instrument, FeatureReadRequest request) {
        Map<String, IndicatorValue> latestIndicators = new HashMap<>();
        Map<String, IndicatorValue> previousIndicators = new HashMap<>();
        Map<String, BigDecimal> previousPrices = new HashMap<>();
        Boolean priceAsked = request.usesPriceOperand();
        Long instrumentId = instrument.getId();
        for (FeatureBinding binding : emptyIfNull(request.getIndicatorBindings())) {
            indicatorService.getLatestValue(instrumentId, binding.getConfigId(), binding.getTolerance())
                    .ifPresent(value -> latestIndicators.put(binding.getKey(), value));
            Optional<IndicatorValue> previous = indicatorService.getPreviousValue(instrumentId, binding.getConfigId());
            previous.ifPresent(value -> previousIndicators.put(binding.getKey(), value));
            previous.flatMap(value -> previousPrice(instrumentId, binding.getConfigId(), value, priceAsked))
                    .ifPresent(close -> previousPrices.put(binding.getKey(), close));
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
                .previousPrices(previousPrices)
                .structures(structures)
                .price(isNull(priceData) ? null : priceData.getExternalLastPrice())
                .evaluationTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        return MarketFeatureBundle.builder()
                .latestIndicators(latestIndicators)
                .previousIndicators(previousIndicators)
                .previousPrices(previousPrices)
                .structures(structures)
                .marketPriceData(priceData)
                .marketPhase(marketPhaseService.resolve(instrumentId, request.getPhaseRules(), context)
                        .orElse(null))
                .build();
    }

    /**
     * Прошлое цены для привязки индикатора — закрытие свечи, на которой
     * посчитано его предыдущее значение; пусто — цену не спрашивали либо
     * свечи этой метки в ряду нет.
     *
     * <p><b>Спрашивается вместе с ценой момента, а не всегда.</b> Без цены
     * момента пересечение с ценой ложно при любом прошлом, и чтение свечи на
     * каждую привязку было бы работой, которую никто не прочтёт. Какая именно
     * привязка стоит в паре с ценой, market-data не знает — предикатов шагов
     * он не получает, — и поэтому прошлое цены отдаётся по всем привязкам.
     *
     * <p>Отсутствие не выдумывается: нет предыдущего значения (история
     * короче двух значений) либо его свечи — ключа нет, и оценщик читает
     * пересечение ложью.
     */
    private Optional<BigDecimal> previousPrice(Long instrumentId, Long configId, IndicatorValue previous,
                                               Boolean priceAsked) {
        if (isFalse(priceAsked) || isNull(previous.getCandleTimestamp())) {
            return Optional.empty();
        }
        return candleDataService.findCloseOfIndicatorCandle(instrumentId, configId,
                previous.getCandleTimestamp().toInstant().toEpochMilli());
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
