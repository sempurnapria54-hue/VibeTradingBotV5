package com.example.tradingcore.api.controller;

import com.example.tradingcore.domain.jobs.facade.AnomalyJobFacade;
import com.example.tradingcore.domain.jobs.facade.DealOrchestratorJobFacade;
import com.example.tradingcore.domain.jobs.facade.EntryScannerJobFacade;
import com.example.tradingcore.domain.jobs.facade.OutboxRelayJobFacade;
import com.example.tradingcore.domain.jobs.facade.RegistryProjectionJobFacade;
import com.example.tradingcore.domain.jobs.facade.StrategyDemandJobFacade;
import com.example.tradingcore.domain.jobs.facade.TradeFeeRateSyncJobFacade;
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
 * Ручной запуск тиков ядра вне расписания.
 *
 * <p><b>{@code 202}, а не {@code 200}: фасад отвечает только за
 * ЗАПУСК.</b> Исход самой работы наружу не транслируется и уходит во
 * внутреннюю градацию (docs/rules/error-handling-policy.md,
 * .claude/rules/codestyle.md §«Обработка ошибок»). Перекрывающий запуск
 * гасит защита от конкурентного выполнения — молча, потому что пропуск
 * перекрытия и есть штатное поведение.
 */
@RestController
@RequestMapping("/api/v1/trading-core/jobs")
@RequiredArgsConstructor
public class JobController {

    private final DealOrchestratorJobFacade dealOrchestratorJobFacade;
    private final EntryScannerJobFacade entryScannerJobFacade;
    private final AnomalyJobFacade anomalyJobFacade;
    private final OutboxRelayJobFacade outboxRelayJobFacade;
    private final RegistryProjectionJobFacade registryProjectionJobFacade;
    private final StrategyDemandJobFacade strategyDemandJobFacade;
    private final TradeFeeRateSyncJobFacade tradeFeeRateSyncJobFacade;

    @Operation(summary = "Запустить проход сопровождения сделок")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Проход запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/deal-orchestrator")
    public void triggerDealOrchestrator() {
        dealOrchestratorJobFacade.trigger();
    }

    @Operation(summary = "Запустить отбор входа")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/entry-scanner")
    public void triggerEntryScanner() {
        entryScannerJobFacade.trigger();
    }

    @Operation(summary = "Запустить проход проактивной детекции")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Проход запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/anomaly-detection")
    public void triggerAnomalyDetection() {
        anomalyJobFacade.trigger();
    }

    @Operation(summary = "Запустить реле outbox")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/outbox-relay")
    public void triggerOutboxRelay() {
        outboxRelayJobFacade.trigger();
    }

    @Operation(summary = "Запустить синк проекций чужих реестров")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/registry-projections")
    public void triggerRegistryProjections() {
        registryProjectionJobFacade.trigger();
    }

    @Operation(summary = "Запустить объявление потребности у владельца рыночных данных")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/strategy-demand")
    public void triggerStrategyDemand() {
        strategyDemandJobFacade.trigger();
    }

    @Operation(summary = "Запустить синк ставок комиссии счетов")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Тик запущен"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/trade-fee-rates")
    public void triggerTradeFeeRates() {
        tradeFeeRateSyncJobFacade.trigger();
    }
}
