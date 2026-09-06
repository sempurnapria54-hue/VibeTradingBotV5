package com.example.tradingcore.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingcore.config.StrategyDemandProperties;
import com.example.tradingcore.domain.service.StrategyDemandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Тик объявления потребности копий определения владельцу рыночных данных.
 *
 * <p><b>Работы у тика ровно столько, сколько непривязанных объявлений.</b>
 * Копия неизменяема, идентичность вычисления пишется write-once, поэтому
 * привязанное объявление тик больше не трогает: в установившемся состоянии
 * проход пуст и стоит одного запроса.
 *
 * <p><b>Такт частый, и направление калибровки названо: ЧАЩЕ, чем реже.</b>
 * Пока потребность не объявлена, стратегия не торгует — её условия читать
 * нечем, — и цена задержки есть простой стратегии. Само чтение
 * внутрикластерное и лимита площадки не тратит.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyDemandJob {

    private static final String JOB_NAME = "strategyDemandJob";

    private final StrategyDemandService demandService;
    private final StrategyDemandProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${strategy-demand.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        try {
            log.debug("Strategy demands declared: {} copies", demandService.declarePendingDemands());
        } catch (RuntimeException failure) {
            log.error("Strategy demand tick failed", failure);
        }
    }
}
