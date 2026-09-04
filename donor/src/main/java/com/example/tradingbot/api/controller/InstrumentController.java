package com.example.tradingbot.api.controller;

import com.example.tradingbot.api.model.request.CreateInstrumentApiRequest;
import com.example.tradingbot.api.model.response.InstrumentApiResponse;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.service.core.ExchangeService;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.mapping.InstrumentMapper;
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
 * API инструментов: заведение и онбординг-переходы (SYNC спецификации,
 * запуск загрузки свечей). Наружу адресуется по internalId; в ответе —
 * internalId инструмента и exchangeInternalId биржи (резолвится из
 * доменного exchangeId). Автономного драйвера онбординга нет —
 * последовательность триггерится явно (ORCH-Q1). Поверхность закрыта контуром доступа
 * (docs/rules/api-access-policy.md): умолчание закрыто, принципал один.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instruments")
@Tag(name = "Instruments", description = "Инструменты и онбординг")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final ExchangeService exchangeService;
    private final InstrumentMapper mapper;

    @PostMapping
    @Operation(summary = "Завести инструмент (CREATED)")
    public InstrumentApiResponse create(@Valid @RequestBody CreateInstrumentApiRequest request) {
        Instrument instrument = instrumentService.create(mapper.apiToDomain(request), request.getExchangeInternalId());
        return toResponse(instrument);
    }

    @GetMapping("/{internalId}")
    @Operation(summary = "Получить инструмент")
    public InstrumentApiResponse getByInternalId(
            @Parameter(description = "Межсервисный идентификатор инструмента", required = true)
            @PathVariable @NotBlank String internalId) {
        return toResponse(instrumentService.getRequiredByInternalId(internalId));
    }

    @PostMapping("/{internalId}/specification-sync")
    @Operation(summary = "Синхронизировать спецификацию с биржей (SYNC)")
    public InstrumentApiResponse synchronizeSpecification(
            @Parameter(description = "Межсервисный идентификатор инструмента", required = true)
            @PathVariable @NotBlank String internalId) {
        return toResponse(instrumentService.synchronizeSpecification(internalId));
    }

    @PostMapping("/{internalId}/candles-loading")
    @Operation(summary = "Запустить загрузку свечей (CANDLES_LOADING)")
    public InstrumentApiResponse startCandlesLoading(
            @Parameter(description = "Межсервисный идентификатор инструмента", required = true)
            @PathVariable @NotBlank String internalId) {
        return toResponse(instrumentService.startCandlesLoading(internalId));
    }

    @PostMapping("/{internalId}/trade-unblock")
    @Operation(summary = "Снять safety-холд инструмента (TRADE_BLOCKED → ACTIVE)")
    public InstrumentApiResponse unblockTrade(
            @Parameter(description = "Межсервисный идентификатор инструмента", required = true)
            @PathVariable @NotBlank String internalId) {
        return toResponse(instrumentService.unblockTrade(internalId));
    }

    @PostMapping("/{internalId}/entry-unblock")
    @Operation(summary = "Снять мягкую safety-ступень инструмента (ENTRY_BLOCKED → ACTIVE)")
    public InstrumentApiResponse unblockEntry(
            @Parameter(description = "Межсервисный идентификатор инструмента", required = true)
            @PathVariable @NotBlank String internalId) {
        return toResponse(instrumentService.unblockEntry(internalId));
    }

    private InstrumentApiResponse toResponse(Instrument instrument) {
        String exchangeInternalId = exchangeService.getRequiredInternalIdById(instrument.getExchangeId());
        return mapper.domainToApi(instrument, exchangeInternalId);
    }
}
