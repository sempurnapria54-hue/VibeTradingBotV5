package com.example.marketdata.domain.model;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Запрос классификации фазы рынка: клаузы потребителя плюс привязки их
 * операндов к идентичностям вычисления.
 *
 * <p><b>Клаузы приезжают операндом вызова, а не читаются из чужой
 * базы.</b> market-data потребителем определений стратегий не является и
 * чужую модель определения не разбирает
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»): он получает предикат и считает его на СВОИХ данных — та же
 * форма, что у толерантности свежести.
 *
 * <p>Фаза не персистируется: вычисляется на лету на момент запроса
 * (docs/rules/market-data-retention.md).
 */
@Getter
@Builder
public class MarketPhaseRequest {

    /** Авторские клаузы классификации, first-match по позиции в списке. */
    private final List<StrategyMarketPhaseRule> phaseRules;

    /** Привязки индикаторных операндов к идентичностям вычисления. */
    private final List<FeatureBinding> indicatorBindings;

    /** Привязки структурных операндов к идентичностям вычисления. */
    private final List<FeatureBinding> structureBindings;
}
