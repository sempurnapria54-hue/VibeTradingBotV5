package com.example.marketdata.api.controller;

import com.example.marketdata.domain.jobs.facade.CandleJobFacade;
import com.example.marketdata.domain.jobs.facade.IndicatorJobFacade;
import com.example.marketdata.domain.jobs.facade.InstrumentSyncJobFacade;
import com.example.marketdata.domain.jobs.facade.MarketStructureJobFacade;
import com.example.marketdata.domain.jobs.facade.SnapshotCollectionJobFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ручной запуск тиков вне расписания.
 *
 * <p><b>{@code 202}, а не {@code 200}: фасад отвечает только за
 * ЗАПУСК.</b> Исход самой работы наружу не транслируется и уходит во
 * внутреннюю градацию (docs/rules/error-handling-policy.md,
 * .claude/rules/codestyle.md §«Обработка ошибок»). Перекрывающий запуск
 * гасит {@link com.example.marketdata.domain.jobs.JobExecutionGuard} —
 * молча, потому что пропуск перекрытия и есть штатное поведение.
 */
@RestController
@RequestMapping("/api/v1/market-data/jobs")
@RequiredArgsConstructor
public class JobController {

    private final InstrumentSyncJobFacade instrumentSyncJobFacade;
    private final CandleJobFacade candleJobFacade;
    private final IndicatorJobFacade indicatorJobFacade;
    private final MarketStructureJobFacade marketStructureJobFacade;
    private final SnapshotCollectionJobFacade snapshotCollectionJobFacade;

    @Operation(summary = "Запустить синк листинга и справочных правил")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/instrument-sync")
    public void triggerInstrumentSync() {
        instrumentSyncJobFacade.trigger();
    }

    @Operation(summary = "Запустить тик загрузки свечей")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/candles")
    public void triggerCandles() {
        candleJobFacade.trigger();
    }

    @Operation(summary = "Запустить тик расчёта индикаторов")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/indicators")
    public void triggerIndicators() {
        indicatorJobFacade.trigger();
    }

    @Operation(summary = "Запустить тик расчёта структуры рынка")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/market-structures")
    public void triggerMarketStructures() {
        marketStructureJobFacade.trigger();
    }

    @Operation(summary = "Запустить проход сбора невосполнимых срезов")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Проход запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/snapshots")
    public void triggerSnapshots() {
        snapshotCollectionJobFacade.trigger();
    }
}
