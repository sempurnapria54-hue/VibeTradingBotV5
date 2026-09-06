package com.example.tradingcore.domain.jobs.facade;

import com.example.tradingcore.domain.jobs.TradeFeeRateSyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link TradeFeeRateSyncJob} вне расписания: ручной
 * триггер синка ставок комиссии не блокирует HTTP-ответ
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
public class TradeFeeRateSyncJobFacade {

    private final TradeFeeRateSyncJob tradeFeeRateSyncJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual TradeFeeRateSyncJob trigger started");
        tradeFeeRateSyncJob.tick();
        log.info("Manual TradeFeeRateSyncJob trigger finished");
    }
}
