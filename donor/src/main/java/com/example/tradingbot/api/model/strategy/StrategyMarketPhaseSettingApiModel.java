package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Настройка классификации фазы рынка (API): одна на стратегию, фаза нужна
 * до выбора детали. Фаза вычисляется на лету и не персистируется — своих
 * timeframe / expirationDuration у неё нет (трек D).
 */
@Getter
@Setter
public class StrategyMarketPhaseSettingApiModel {

    @Valid
    @NotEmpty
    @Schema(description = "Авторские клаузы классификации фазы (first-match по позиции в списке); "
            + "не сработала ни одна → UNKNOWN; операнды ссылаются на настройки стратегии по key",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyMarketPhaseRuleApiModel> phaseRules;
}
