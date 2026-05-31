package com.example.tradingbot.api.controller;

import com.example.tradingbot.api.model.response.CandleGroupApiResponse;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.mapping.CandleGroupMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API групп свечей инструмента (чтение статуса загрузки/покрытия).
 * Авторизация — шаг 9 «Безопасность».
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instruments/{instrumentId}/candle-groups")
@Tag(name = "CandleGroups", description = "Группы свечей инструмента")
public class CandleGroupController {

    private final InstrumentService instrumentService;
    private final CandleGroupMapper mapper;

    @GetMapping
    @Operation(summary = "Список групп свечей инструмента")
    public List<CandleGroupApiResponse> getByInstrument(@PathVariable Long instrumentId) {
        return instrumentService.getCandleGroups(instrumentId).stream()
                .map(mapper::domainToApi)
                .toList();
    }
}
