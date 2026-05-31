package com.example.tradingbot.api.controller;

import com.example.tradingbot.domain.jobs.CandleJobFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API ручного запуска джоб вне расписания. Запуск асинхронный (через
 * фасад) — ответ не блокируется выполнением джобы (отдаётся 202
 * Accepted). Авторизация (@PreAuthorize) — на шаге 9 «Безопасность».
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Ручной запуск джоб вне расписания")
public class JobController {

    private final CandleJobFacade candleJobFacade;

    @PostMapping("/candle-loading/trigger")
    @Operation(summary = "Запустить загрузку свечей вне расписания (асинхронно)")
    public ResponseEntity<Void> triggerCandleLoading() {
        candleJobFacade.trigger();
        return ResponseEntity.accepted().build();
    }
}
