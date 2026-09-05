package com.example.marketdata.api.model;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос классификации фазы: клаузы потребителя и привязки их операндов.
 *
 * <p><b>Клаузы едут операндом вызова, а не читаются из чужой базы.</b>
 * market-data потребителем определений стратегий не является
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»): он получает предикат и считает его на своих данных.
 */
@Getter
@Setter
public class MarketPhaseApiRequest {

    @NotEmpty
    @Schema(description = "Авторские клаузы классификации; побеждает первая истинная по порядку")
    private List<StrategyMarketPhaseRule> phaseRules;

    @Valid
    @Schema(description = "Привязки индикаторных операндов к идентичностям вычисления")
    private List<FeatureBindingApiRequest> indicatorBindings;

    @Valid
    @Schema(description = "Привязки структурных операндов к идентичностям вычисления")
    private List<FeatureBindingApiRequest> structureBindings;
}
