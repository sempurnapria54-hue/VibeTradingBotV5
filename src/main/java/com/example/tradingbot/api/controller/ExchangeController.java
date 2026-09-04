package com.example.tradingbot.api.controller;

import com.example.tradingbot.api.model.request.CreateExchangeApiRequest;
import com.example.tradingbot.api.model.response.ExchangeApiResponse;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.service.core.ExchangeService;
import com.example.tradingbot.mapping.ExchangeMapper;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API бирж. Контроллер принимает api, делает apiToDomain, дальше
 * работает только с domain (codestyle: слои). Наружу адресуется по
 * internalId. Поверхность закрыта контуром доступа
 * (docs/rules/api-access-policy.md): умолчание закрыто, принципал один.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exchanges")
@Tag(name = "Exchanges", description = "Биржи")
public class ExchangeController {

    private final ExchangeService exchangeService;
    private final ExchangeMapper mapper;

    @PostMapping
    @Operation(summary = "Завести биржу")
    public ExchangeApiResponse create(@Valid @RequestBody CreateExchangeApiRequest request) {
        Exchange exchange = exchangeService.create(mapper.apiToDomain(request));
        return mapper.domainToApi(exchange);
    }

    @GetMapping("/{internalId}")
    @Operation(summary = "Получить биржу")
    public ExchangeApiResponse getByInternalId(
            @Parameter(description = "Межсервисный идентификатор биржи", required = true)
            @PathVariable @NotBlank String internalId) {
        return mapper.domainToApi(exchangeService.getRequiredByInternalId(internalId));
    }

    @PostMapping("/{internalId}/trade-unblock")
    @Operation(summary = "Снять сворачивание биржи — первый из двух ходов (TRADE_BLOCKED → HOLD; "
            + "требует машинно доказанного отсутствия живого риска)")
    public ExchangeApiResponse unblockTrade(
            @Parameter(description = "Межсервисный идентификатор биржи", required = true)
            @PathVariable @NotBlank String internalId) {
        return mapper.domainToApi(exchangeService.unblockTrade(internalId));
    }

    @PostMapping("/{internalId}/hold-clear")
    @Operation(summary = "Снять мягкую ступень биржи — второй ход (HOLD → ACTIVE; "
            + "каскадно возвращает инструменты в выборку входа)")
    public ExchangeApiResponse clearHold(
            @Parameter(description = "Межсервисный идентификатор биржи", required = true)
            @PathVariable @NotBlank String internalId) {
        return mapper.domainToApi(exchangeService.clearHold(internalId));
    }
}
