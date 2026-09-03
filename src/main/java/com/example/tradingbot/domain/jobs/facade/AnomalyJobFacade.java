package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.AnomalyJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async-фасад ручного триггера {@link AnomalyJob} вне расписания: не
 * блокирует HTTP-ответ. Делегирует в {@code tick()} (enabled-гейт и
 * in-memory guard применяются и при ручном заходе). Отвечает только за
 * запуск (docs/rules/error-handling-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyJobFacade {

    private final AnomalyJob anomalyJob;

    @Async
    public void trigger() {
        log.info("Manual anomaly detection trigger started");
        anomalyJob.tick();
        log.info("Manual anomaly detection trigger finished");
    }
}
