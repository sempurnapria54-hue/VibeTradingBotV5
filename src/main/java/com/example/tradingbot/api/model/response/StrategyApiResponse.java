package com.example.tradingbot.api.model.response;

import com.example.tradingbot.api.model.strategy.StrategyDetailApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketPhaseSettingApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Представление стратегии в API нашего сервиса (полное дерево).
 * Наружу — internalId стратегии и instrumentInternalId инструмента,
 * не id из БД.
 */
@Getter
@Setter
public class StrategyApiResponse extends AuditableApiResponse {

    @Schema(description = "Межсервисный идентификатор стратегии")
    private String internalId;

    @Schema(description = "Межсервисный идентификатор инструмента стратегии")
    private String instrumentInternalId;

    @Schema(description = "Человекочитаемое имя стратегии")
    private String name;

    @Schema(description = "Административный статус: CREATED/ACTIVE/INACTIVE/DELETED")
    private String status;

    @Schema(description = "Настройка расчёта фазы рынка")
    private StrategyMarketPhaseSettingApiModel marketPhaseSetting;

    @Schema(description = "Детали по фазам рынка")
    private List<StrategyDetailApiModel> details;
}
