package com.example.tradingbot.domain.service.market;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.service.market.phase.MarketPhaseResolver;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Отдаёт актуальную фазу рынка потребителям (EntryScannerJob выбирает
 * StrategyDetail по MarketPhase.Type). Фаза не персистируется —
 * вычисляется на лету на момент запроса: сервис собирает текущие свежие
 * IndicatorValue / MarketStructure по strategy-scope-настройкам стратегии
 * (трек D) и резолвит тип через MarketPhaseResolver. Своего срока свежести
 * у фазы нет — свежесть наследуется от входов: устаревший/отсутствующий
 * вход не попадает в контекст (IndicatorService/MarketStructureService
 * гейтят свежесть) → операнд недоступен → консервативный UNKNOWN. См.
 * docs/components/MarketPhaseService.md,
 * docs/decisions/market-phase-stateless.md.
 */
@Service
@RequiredArgsConstructor
public class MarketPhaseService {

    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;
    private final MarketPhaseResolver resolver;

    /** Текущая фаза, вычисленная на лету по свежим входам (пусто — нет правил классификации). */
    public Optional<MarketPhase> getCurrentPhase(Strategy strategy) {
        StrategyMarketPhaseSetting setting = strategy.getMarketPhaseSetting();
        if (isNull(setting) || isEmpty(setting.getPhaseRules())) {
            return Optional.empty();
        }
        ConditionEvaluationContext context = buildContext(strategy);
        MarketPhase phase = new MarketPhase();
        phase.setInstrumentId(strategy.getInstrumentId());
        phase.setType(resolver.resolve(setting.getPhaseRules(), context));
        return Optional.of(phase);
    }

    private ConditionEvaluationContext buildContext(Strategy strategy) {
        Long instrumentId = strategy.getInstrumentId();
        Map<String, IndicatorValue> latestIndicators = new HashMap<>();
        Map<String, IndicatorValue> previousIndicators = new HashMap<>();
        for (StrategyIndicatorSetting indicatorSetting : nullSafe(strategy.getIndicatorSettings())) {
            indicatorService.getLatestValue(instrumentId, indicatorSetting)
                    .ifPresent(value -> latestIndicators.put(indicatorSetting.getKey(), value));
            indicatorService.getPreviousValue(instrumentId, indicatorSetting)
                    .ifPresent(value -> previousIndicators.put(indicatorSetting.getKey(), value));
        }
        Map<String, MarketStructure> structures = new HashMap<>();
        for (StrategyMarketStructureSetting structureSetting : nullSafe(strategy.getMarketStructureSettings())) {
            marketStructureService.getLatestStructure(instrumentId, structureSetting)
                    .ifPresent(structure -> structures.put(structureSetting.getKey(), structure));
        }
        return ConditionEvaluationContext.builder()
                .latestIndicators(latestIndicators)
                .previousIndicators(previousIndicators)
                .structures(structures)
                .evaluationTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private <T> List<T> nullSafe(List<T> source) {
        return isNull(source) ? List.of() : source;
    }
}
