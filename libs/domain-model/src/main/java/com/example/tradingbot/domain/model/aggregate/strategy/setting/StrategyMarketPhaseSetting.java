package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.Auditable;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Настройка классификации фазы рынка. Живёт на уровне Strategy (одна на
 * стратегию), потому что фаза нужна до выбора StrategyDetail: одна
 * настройка описывает классификацию рынка во все MarketPhase.Type.
 * Фаза вычисляется на лету и не персистируется — своих часов (timeframe)
 * и срока свежести (expirationDuration) у неё нет; свежесть наследуется от
 * входов (трек D, docs/components/MarketPhaseService.md). Каркасный
 * реляционный узел дерева (своя строка/таблица); phaseRules и листовые
 * настройки — JSONB на его строке. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyMarketPhaseSetting).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyMarketPhaseSetting extends Auditable {

    /** Технический ID узла. */
    private Long id;

    /**
     * Авторские правила классификации фазы (упорядоченный first-match-
     * список клауз «условие → фаза»; порядок = позиция в списке). Заменяют
     * распущенный скоринговый MarketPhaseParams
     * (docs/components/MarketPhaseResolver.md).
     * Операнды клауз адресуют индикаторы/структуры стратегии по
     * {@code key} (strategy-scope, трек D).
     */
    private List<StrategyMarketPhaseRule> phaseRules;
}
