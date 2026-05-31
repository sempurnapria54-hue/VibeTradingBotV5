package com.example.tradingbot.api.controller;

import com.example.tradingbot.api.model.request.CreateInstrumentApiRequest;
import com.example.tradingbot.api.model.response.InstrumentApiResponse;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.mapping.InstrumentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * запуск загрузки свечей). Автономного драйвера онбординга нет —
 * последовательность триггерится явно (ORCH-Q1). Авторизация — шаг 9.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instruments")
@Tag(name = "Instruments", description = "Инструменты и онбординг")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final InstrumentMapper mapper;

    @PostMapping
    @Operation(summary = "Завести инструмент (CREATED)")
    public InstrumentApiResponse create(@Valid @RequestBody CreateInstrumentApiRequest request) {
        Instrument instrument = instrumentService.create(mapper.apiToDomain(request));
        return mapper.domainToApi(instrument);
    }

    @GetMapping("/{instrumentId}")
    @Operation(summary = "Получить инструмент")
    public InstrumentApiResponse getById(@PathVariable Long instrumentId) {
        return mapper.domainToApi(instrumentService.getRequiredById(instrumentId));
    }

    @PostMapping("/{instrumentId}/specification-sync")
    @Operation(summary = "Синхронизировать спецификацию с биржей (SYNC)")
    public InstrumentApiResponse synchronizeSpecification(@PathVariable Long instrumentId) {
        return mapper.domainToApi(instrumentService.synchronizeSpecification(instrumentId));
    }

    @PostMapping("/{instrumentId}/candles-loading")
    @Operation(summary = "Запустить загрузку свечей (CANDLES_LOADING)")
    public InstrumentApiResponse startCandlesLoading(@PathVariable Long instrumentId) {
        return mapper.domainToApi(instrumentService.startCandlesLoading(instrumentId));
    }
}
