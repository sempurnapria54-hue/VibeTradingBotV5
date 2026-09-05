package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.marketdata.domain.model.FeatureBinding;
import com.example.marketdata.domain.model.MarketPhaseRequest;
import com.example.marketdata.domain.service.phase.MarketPhaseResolver;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Отдаёт актуальную фазу рынка потребителю. Фаза не персистируется —
 * вычисляется на лету на момент запроса: сервис собирает свежие значения
 * по привязкам запроса и резолвит тип через {@link MarketPhaseResolver}.
 * См. docs/components/MarketPhaseService.md,
 * docs/decisions/market-phase-stateless.md.
 *
 * <p><b>Своего срока свежести у фазы нет</b> — свежесть наследуется от
 * входов: устаревший либо отсутствующий вход в контекст не попадает,
 * операнд оказывается недоступен, и результат — консервативный
 * {@code UNKNOWN}.
 *
 * <p><b>Клаузы и привязки приносит потребитель</b>
 * ({@link MarketPhaseRequest}): market-data не знает ни стратегий, ни их
 * настроек, и знать не должен.
 */
@Service
@RequiredArgsConstructor
public class MarketPhaseService {

    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;
    private final MarketPriceDataService marketPriceDataService;
    private final MarketPhaseResolver resolver;

    /** Текущая фаза, вычисленная на лету по свежим входам (пусто — нет правил классификации). */
    public Optional<MarketPhase> getCurrentPhase(Instrument instrument, MarketPhaseRequest request) {
        if (isNull(request) || isEmpty(request.getPhaseRules())) {
            return Optional.empty();
        }
        ConditionEvaluationContext context = buildContext(instrument, request);
        MarketPhase phase = new MarketPhase();
        phase.setInstrumentId(instrument.getId());
        phase.setType(resolver.resolve(request.getPhaseRules(), context));
        return Optional.of(phase);
    }

    private ConditionEvaluationContext buildContext(Instrument instrument, MarketPhaseRequest request) {
        Long instrumentId = instrument.getId();
        Map<String, IndicatorValue> latestIndicators = new HashMap<>();
        Map<String, IndicatorValue> previousIndicators = new HashMap<>();
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
        MarketPriceData marketPriceData = marketPriceDataService.getMarketPriceData(
                instrumentId, instrument.getExternalId());
        return ConditionEvaluationContext.builder()
                .latestIndicators(latestIndicators)
                .previousIndicators(previousIndicators)
                .structures(structures)
                .price(isNull(marketPriceData) ? null : marketPriceData.getExternalLastPrice())
                .evaluationTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
