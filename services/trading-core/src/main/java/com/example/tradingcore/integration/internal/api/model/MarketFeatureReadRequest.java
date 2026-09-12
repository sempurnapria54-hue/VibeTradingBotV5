package com.example.tradingcore.integration.internal.api.model;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Тело запроса фич на момент решения владельцу рыночных данных
 * (docs/architecture/contracts.md §«Синхронные вызовы»).
 *
 * <p><b>Привязки и клаузы едут операндом вызова.</b> Владелец данных
 * определений стратегий не читает: он получает предикат и идентичности и
 * считает их на своих данных
 * (docs/architecture/market-data-collection.md).
 *
 * <p>Значение только пишется — читателя у него на нашей стороне нет,
 * поэтому неизменяемая форма здесь законна
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Getter
@Builder
public class MarketFeatureReadRequest {

    /** Привязки индикаторных операндов к идентичностям вычисления. */
    private final List<MarketFeatureBinding> indicatorBindings;

    /** Привязки структурных операндов к идентичностям вычисления. */
    private final List<MarketFeatureBinding> structureBindings;

    /** Клаузы классификации фазы; пусто — фаза не спрашивается. */
    private final List<StrategyMarketPhaseRule> phaseRules;

    /** Нужна ли цена момента сверх той, что потребовали бы клаузы фазы. */
    private final Boolean priceRequired;
}
