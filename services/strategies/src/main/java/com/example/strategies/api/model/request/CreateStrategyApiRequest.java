package com.example.strategies.api.model.request;

import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyIndicatorSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketPhaseSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketStructureSettingApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на создание определения стратегии (полное immutable-дерево).
 *
 * <p><b>Тело несёт не весь корень.</b> Статус проставляет система
 * ({@code CREATED} — он же черновик), а <b>тенанта тело не несёт
 * вовсе</b>: он приходит контекстом вызова и сверяется с членствами, а
 * принятый из тела был бы объявлением вызывающего о самом себе
 * (docs/models/domain/aggregate/Strategy.md §«У каждого поля контекста
 * назван писатель и момент»). Счёт и инструмент, наоборот, —
 * содержательный выбор автора, и их место здесь.
 *
 * <p><b>Идентичность стратегии телом тоже не приходит:</b> её присваивает
 * сервис-владелец до первой записи, и она попадает в ответ и в исходящее
 * событие (docs/architecture/data-ownership.md §Идентификаторы). Принятая
 * от вызывающего, она позволила бы ему выбрать имя чужой сущности.
 *
 * <p>Проверка создания — структурно-ссылочная плюс пять неравенств
 * (400); торгово-суждённые диапазоны и готовность к запуску — семантика
 * активации (docs/rules/strategy-validation.md).
 */
@Getter
@Setter
public class CreateStrategyApiRequest {

    @NotBlank
    @Schema(description = "Межсервисный идентификатор биржевого счёта, на котором стратегия торгует",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String exchangeAccountInternalId;

    @NotBlank
    @Schema(description = "Межсервисный идентификатор инструмента стратегии",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String instrumentInternalId;

    @NotBlank
    @Schema(description = "Человекочитаемое имя стратегии", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Valid
    @NotNull
    @Schema(description = "Настройка расчёта фазы рынка (одна на стратегию)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private StrategyMarketPhaseSettingApiModel marketPhaseSetting;

    @Valid
    @NotEmpty
    @Schema(description = "Детали по фазам рынка: ровно одна на каждый MarketPhase.Type",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyDetailApiModel> details;

    @Valid
    @Schema(description = "Настройки индикаторов стратегии (strategy-scope, объявлены раз; "
            + "фаза/детали/действия ссылаются по key)")
    private List<StrategyIndicatorSettingApiModel> indicatorSettings;

    @Valid
    @Schema(description = "Настройки структуры рынка стратегии (strategy-scope, адресуются по key)")
    private List<StrategyMarketStructureSettingApiModel> marketStructureSettings;
}
