package com.example.marketdata.domain.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.marketdata.domain.service.phase.MarketPhaseResolver;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Классифицирует фазу рынка по клаузам потребителя. Фаза не
 * персистируется — вычисляется на лету на момент запроса.
 * См. docs/components/MarketPhaseService.md,
 * docs/components/MarketPhaseResolver.md.
 *
 * <p><b>Входы приезжают готовым контекстом, а не собираются здесь.</b>
 * Собирает их чтение фич ({@link MarketFeatureService}): те же значения
 * нужны и условиям шагов, и калькуляторам, и вторая сборка была бы вторым
 * носителем одной истины — с собственным моментом снятия.
 *
 * <p><b>Своего срока свежести у фазы нет</b> — свежесть наследуется от
 * входов: устаревший либо отсутствующий вход в контекст не попадает,
 * операнд оказывается недоступен, и результат — консервативный
 * {@code UNKNOWN}.
 *
 * <p><b>Клаузы приносит потребитель:</b> market-data не знает ни
 * стратегий, ни их настроек, и знать не должен.
 */
@Service
@RequiredArgsConstructor
public class MarketPhaseService {

    private final MarketPhaseResolver resolver;

    /** Фаза по клаузам потребителя на готовых входах (пусто — клауз нет). */
    public Optional<MarketPhase> resolve(Long instrumentId, List<StrategyMarketPhaseRule> phaseRules,
                                         ConditionEvaluationContext context) {
        if (isEmpty(phaseRules)) {
            return Optional.empty();
        }
        MarketPhase phase = new MarketPhase();
        phase.setInstrumentId(instrumentId);
        phase.setType(resolver.resolve(phaseRules, context));
        return Optional.of(phase);
    }
}
