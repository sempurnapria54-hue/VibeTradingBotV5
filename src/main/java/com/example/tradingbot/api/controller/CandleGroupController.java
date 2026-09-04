package com.example.tradingbot.api.controller;

import static java.util.stream.Collectors.toList;

import com.example.tradingbot.api.model.response.CandleGroupApiResponse;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.mapping.CandleGroupMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API групп свечей инструмента (чтение статуса загрузки/покрытия).
 * Инструмент адресуется по internalId; в ответе — internalId группы и
 * instrumentInternalId. Поверхность закрыта контуром доступа
 * (docs/rules/api-access-policy.md): умолчание закрыто, принципал один.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instruments/{instrumentInternalId}/candle-groups")
@Tag(name = "CandleGroups", description = "Группы свечей инструмента")
public class CandleGroupController {

    private final InstrumentService instrumentService;
    private final CandleGroupMapper mapper;

    @GetMapping
    @Operation(summary = "Список групп свечей инструмента")
    public List<CandleGroupApiResponse> getByInstrument(
            @Parameter(description = "Межсервисный идентификатор инструмента", required = true)
            @PathVariable @NotBlank String instrumentInternalId) {
        return instrumentService.getCandleGroups(instrumentInternalId).stream()
                .map(group -> mapper.domainToApi(group, instrumentInternalId))
                .collect(toList());
    }
}
