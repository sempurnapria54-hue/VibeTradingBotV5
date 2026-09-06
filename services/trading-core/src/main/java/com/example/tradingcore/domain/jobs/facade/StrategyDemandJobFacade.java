package com.example.tradingcore.domain.jobs.facade;

import com.example.tradingcore.domain.jobs.StrategyDemandJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link StrategyDemandJob} вне расписания: ручной
 * триггер объявления потребности у market-data не блокирует HTTP-ответ
 * (.claude/rules/codestyle.md §Джобы).
 *
 * <p>Фасад отвечает только за ЗАПУСК; исход самой работы наружу не
 * транслируется и уходит во внутреннюю градацию
 * (docs/rules/error-handling-policy.md). Перекрывающий запуск гасит
 * защита от конкурентного выполнения — молча, потому что пропуск
 * перекрытия и есть штатное поведение.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyDemandJobFacade {

    private final StrategyDemandJob strategyDemandJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual StrategyDemandJob trigger started");
        strategyDemandJob.tick();
        log.info("Manual StrategyDemandJob trigger finished");
    }
}
