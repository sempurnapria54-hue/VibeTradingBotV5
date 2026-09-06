package com.example.marketdata.api.model;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос фич на момент решения: привязки операндов плюс то, что читатель
 * хочет получить сверх значений.
 *
 * <p><b>Клаузы едут операндом вызова, а не читаются из чужой базы.</b>
 * market-data потребителем определений стратегий не является
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»): он получает предикат и считает его на своих данных.
 *
 * <p><b>Пустой запрос законен.</b> Читатель, которому нужна одна лишь
 * цена момента, привязок не приносит вовсе; читатель без клауз фазы не
 * получает фазы. Требовать непустоты значило бы запрещать законные
 * чтения.
 */
@Getter
@Setter
public class FeatureReadApiRequest {

    @Valid
    @Schema(description = "Привязки индикаторных операндов к идентичностям вычисления")
    private List<FeatureBindingApiRequest> indicatorBindings;

    @Valid
    @Schema(description = "Привязки структурных операндов к идентичностям вычисления")
    private List<FeatureBindingApiRequest> structureBindings;

    @Schema(description = "Клаузы классификации фазы; пусто — фаза не спрашивается и в ответ не кладётся")
    private List<StrategyMarketPhaseRule> phaseRules;

    @Schema(description = "Нужна ли цена момента сверх той, что потребовали бы клаузы фазы")
    private Boolean priceRequired;
}
