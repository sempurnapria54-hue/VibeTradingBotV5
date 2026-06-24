package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.DealOrchestratorJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async-фасад ручного триггера {@link DealOrchestratorJob} вне расписания:
 * не блокирует HTTP-ответ. Делегирует в {@code tick()} (enabled-гейт и
 * concurrency-guard прохода применяются и при ручном заходе). Отвечает
 * только за запуск (docs/rules/error-handling-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealOrchestratorJobFacade {

    private final DealOrchestratorJob dealOrchestratorJob;

    @Async
    public void trigger() {
        log.info("Manual deal orchestrator trigger started");
        dealOrchestratorJob.tick();
        log.info("Manual deal orchestrator trigger finished");
    }
}
