package com.example.tradingbot.api.controller;

import com.example.tradingbot.api.model.request.CreateStrategyApiRequest;
import com.example.tradingbot.api.model.request.UpdateStrategyStatusApiRequest;
import com.example.tradingbot.api.model.response.StrategyApiResponse;
import com.example.tradingbot.api.validation.StrategyCreateRequestValidator;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.domain.service.strategy.StrategyService;
import com.example.tradingbot.mapping.StrategyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Strategy API — материализация стратегии полным жизненным циклом
 * (docs/decisions/strategy-materialization-and-validation.md):
 * POST — создать (create-валидация 400, структурно-ссылочная);
 * GET — получить; PUT — activate/inactivate/delete (смена статуса,
 * 422 — семантика готовности к запуску). Наружу адресуется по
 * internalId; в ответе — internalId стратегии и instrumentInternalId
 * инструмента. Авторизация — шаг 9.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategies")
@Tag(name = "Strategies", description = "Стратегии: создание, получение, административный статус")
public class StrategyController {

    private final StrategyService strategyService;
    private final InstrumentService instrumentService;
    private final StrategyCreateRequestValidator validator;
    private final StrategyMapper mapper;

    @PostMapping
    @Operation(summary = "Создать стратегию (CREATED, полное immutable-дерево)")
    public StrategyApiResponse create(@Valid @RequestBody CreateStrategyApiRequest request) {
        validator.validateCreate(request);
        Strategy strategy = strategyService.create(mapper.apiToDomain(request), request.getInstrumentInternalId());
        return toResponse(strategy);
    }

    @GetMapping("/{internalId}")
    @Operation(summary = "Получить стратегию (полное дерево)")
    public StrategyApiResponse getByInternalId(
            @Parameter(description = "Межсервисный идентификатор стратегии", required = true)
            @PathVariable @NotBlank String internalId) {
        return toResponse(strategyService.getRequiredByInternalIdWithTree(internalId));
    }

    @PutMapping("/{internalId}/status")
    @Operation(summary = "Сменить административный статус (activate / inactivate / delete)")
    public StrategyApiResponse updateStatus(
            @Parameter(description = "Межсервисный идентификатор стратегии", required = true)
            @PathVariable @NotBlank String internalId,
            @Valid @RequestBody UpdateStrategyStatusApiRequest request) {
        Strategy.Status target = validator.validateStatusUpdate(request);
        return toResponse(strategyService.updateStatus(internalId, target));
    }

    private StrategyApiResponse toResponse(Strategy strategy) {
        String instrumentInternalId = instrumentService.getRequiredInternalIdById(strategy.getInstrumentId());
        return mapper.domainToApi(strategy, instrumentInternalId);
    }
}
