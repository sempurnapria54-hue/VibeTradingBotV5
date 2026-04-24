package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.strategy.StrategyService;
import com.example.tradingbot.mapping.StrategyMapper;
import com.example.tradingbot.rest.error.ApiErrorResponse;
import com.example.tradingbot.rest.model.request.strategy.CreateStrategyRequest;
import com.example.tradingbot.rest.model.response.strategy.StrategyResponse;
import com.example.tradingbot.rest.model.response.strategy.StrategyStatusResponse;
import com.example.tradingbot.rest.model.strategy.StrategyOpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategies")
@Tag(name = "Strategies", description = "Создание, получение и lifecycle-операции над append-only стратегиями.")
public class StrategyController {

    private final StrategyService strategyService;
    private final StrategyMapper strategyMapper;

    @PostMapping
    @Operation(
            summary = "Создать стратегию",
            description = "Создаёт стратегию целиком вместе с details, stepsByStatus, conditions и actions."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Полный append-only объект стратегии на создание.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreateStrategyRequest.class),
                    examples = @ExampleObject(value = StrategyOpenApiExamples.CREATE_REQUEST)
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Стратегия создана в статусе CREATED. internalId сгенерирован сервисом.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StrategyStatusResponse.class),
                            examples = @ExampleObject(value = StrategyOpenApiExamples.STATUS_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидная модель стратегии.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Конфликт сохранения стратегии.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<StrategyStatusResponse> createStrategy(@Valid @RequestBody CreateStrategyRequest request) {
        var strategy = strategyMapper.restToDomain(request);
        var createdStrategy = strategyService.createStrategy(strategy);
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(strategyMapper.domainToRestStatus(createdStrategy));
    }

    @GetMapping("/{internalId}")
    @Operation(
            summary = "Получить стратегию",
            description = "Возвращает стратегию целиком по внешнему идентификатору internalId."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Полная стратегия.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StrategyResponse.class),
                            examples = @ExampleObject(value = StrategyOpenApiExamples.FULL_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Стратегия не найдена.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public StrategyResponse getByInternalId(
            @Parameter(description = "Внешний internalId стратегии.", example = "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7")
            @PathVariable(name = "internalId")
            String internalId
    ) {
        var strategy = strategyService.getByInternalId(internalId);
        return strategyMapper.domainToRest(strategy);
    }

    @PutMapping("/{internalId}/activate")
    @Operation(
            summary = "Активировать стратегию",
            description = "Переводит стратегию в ACTIVE. Если по инструменту уже есть другая ACTIVE-стратегия, она переводится в INACTIVE в той же транзакции."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Статус стратегии после активации.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StrategyStatusResponse.class),
                            examples = @ExampleObject(value = StrategyOpenApiExamples.STATUS_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Стратегия не найдена.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Активация запрещена для текущего статуса стратегии.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Стратегия не проходит валидацию перед активацией.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public StrategyStatusResponse activate(
            @Parameter(description = "Внешний internalId стратегии.", example = "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7")
            @PathVariable(name = "internalId")
            String internalId
    ) {
        var strategy = strategyService.activate(internalId);
        return strategyMapper.domainToRestStatus(strategy);
    }

    @PutMapping("/{internalId}/inactivate")
    @Operation(
            summary = "Деактивировать стратегию",
            description = "Переводит стратегию в INACTIVE. Операция идемпотентна для уже INACTIVE стратегии."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Статус стратегии после деактивации.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StrategyStatusResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "internalId": "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7",
                                      "status": "INACTIVE"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Стратегия не найдена.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Деактивация запрещена для текущего статуса стратегии.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public StrategyStatusResponse inactivate(
            @Parameter(description = "Внешний internalId стратегии.", example = "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7")
            @PathVariable(name = "internalId")
            String internalId
    ) {
        var strategy = strategyService.inactivate(internalId);
        return strategyMapper.domainToRestStatus(strategy);
    }

    @PutMapping("/{internalId}/delete")
    @Operation(
            summary = "Логически удалить стратегию",
            description = "Переводит стратегию в DELETED без физического удаления записи."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Статус стратегии после логического удаления.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StrategyStatusResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "internalId": "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7",
                                      "status": "DELETED"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Стратегия не найдена.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Удаление запрещено для текущего статуса стратегии.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public StrategyStatusResponse delete(
            @Parameter(description = "Внешний internalId стратегии.", example = "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7")
            @PathVariable(name = "internalId")
            String internalId
    ) {
        var strategy = strategyService.delete(internalId);
        return strategyMapper.domainToRestStatus(strategy);
    }
}
