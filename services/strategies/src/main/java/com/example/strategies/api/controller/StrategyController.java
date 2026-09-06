package com.example.strategies.api.controller;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.request.UpdateStrategyStatusApiRequest;
import com.example.strategies.api.model.response.StrategyApiResponse;
import com.example.strategies.config.SurfaceProperties;
import com.example.strategies.domain.service.StrategyCreationService;
import com.example.strategies.domain.service.StrategyLifecycleService;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.strategies.util.Constants;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Поверхность владельца определений: создание, чтение, смена статуса.
 *
 * <p><b>Тенант приходит ЗАГОЛОВКОМ контекста, а не телом и не путём.</b>
 * Контекст вызова несёт тенанта и роль (docs/architecture/contracts.md
 * §«Контекст тенанта в вызове»), и принятый из тела он был бы
 * объявлением вызывающего о самом себе. Все чтения и переходы работают в
 * контексте этого тенанта: чужое определение читается как ненайденное.
 *
 * <p><b>Путь несёт {@code internalId}, а не ключ базы</b>
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 *
 * <p>Умолчание контура — «закрыто»: открытых точек у сервиса нет, кроме
 * пробы живости (docs/rules/api-access-policy.md).
 */
@RestController
@RequestMapping("/api/v1/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyCreationService creationService;
    private final StrategyLifecycleService lifecycleService;
    private final StrategyDefinitionValidator validator;
    private final StrategyDataService strategyDataService;
    private final StrategyApiMapper mapper;
    private final SurfaceProperties surfaceProperties;

    @Operation(summary = "Завести определение стратегии")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Определение заведено"),
            @ApiResponse(responseCode = "400", description = "Определение не прошло проверку создания"),
            @ApiResponse(responseCode = "503", description = "Сосед недоступен: операнд проверки не добыт")})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public StrategyApiResponse create(
            @RequestHeader(Constants.Header.TENANT) @NotBlank String tenantInternalId,
            @Valid @RequestBody CreateStrategyApiRequest request) {
        return mapper.domainToApi(creationService.create(request, tenantInternalId));
    }

    /**
     * Перечень определений тенанта — <b>окном от новых к старым</b>.
     * Размер окна конфигурируем; постраничного обхода у точки нет
     * (`.claude/work/backlog.md` §«Постраничный обход перечня
     * определений»).
     */
    @Operation(summary = "Определения тенанта, окном от новых к старым")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Перечень отдан"))
    @GetMapping
    public List<StrategyApiResponse> findAll(
            @RequestHeader(Constants.Header.TENANT) @NotBlank String tenantInternalId) {
        return strategyDataService.findByTenant(tenantInternalId,
                        surfaceProperties.getStrategyListWindow()).stream()
                .map(mapper::domainToApi)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Определение целиком, вместе с деревом")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Определение отдано"),
            @ApiResponse(responseCode = "404", description = "У тенанта такого определения нет")})
    @GetMapping("/{internalId}")
    public StrategyApiResponse get(
            @RequestHeader(Constants.Header.TENANT) @NotBlank String tenantInternalId,
            @PathVariable String internalId) {
        return strategyDataService.findByInternalIdWithTree(internalId)
                .filter(found -> Objects.equals(tenantInternalId, found.getTenantId()))
                .map(mapper::domainToApi)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Strategy not found: " + internalId));
    }

    /**
     * Смена статуса — единственная тропа жизненного цикла.
     *
     * <p><b>До гейта фазы 4 её инициирует держатель</b>, после — гейт
     * трек-рекорда: он меняет актора, а не саму тропу
     * (docs/lifecycles/Strategy.md §«Кто управляет»).
     */
    @Operation(summary = "Перевести определение в целевой статус")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус переставлен"),
            @ApiResponse(responseCode = "400", description = "Переход недопустим либо не пройдены предусловия"),
            @ApiResponse(responseCode = "404", description = "У тенанта такого определения нет"),
            @ApiResponse(responseCode = "409", description = "На паре уже есть активное определение"),
            @ApiResponse(responseCode = "503", description = "Сосед недоступен: операнд проверки не добыт")})
    @PutMapping("/{internalId}/status")
    public StrategyApiResponse applyStatus(
            @RequestHeader(Constants.Header.TENANT) @NotBlank String tenantInternalId,
            @PathVariable String internalId,
            @Valid @RequestBody UpdateStrategyStatusApiRequest request) {
        Strategy.Status target = validator.validateStatusUpdate(request);
        return mapper.domainToApi(lifecycleService.applyStatus(internalId, tenantInternalId, target));
    }
}
