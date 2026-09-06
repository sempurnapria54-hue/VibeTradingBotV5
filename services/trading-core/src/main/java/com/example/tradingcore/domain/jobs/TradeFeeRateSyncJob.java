package com.example.tradingcore.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingcore.config.TradeFeeRateSyncProperties;
import com.example.tradingcore.domain.service.TradeFeeRateSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Тик синка ставок комиссии счетов.
 *
 * <p><b>Тик свой, а не половина синка проекций.</b> Проекции наполняются
 * чтением у СОСЕДЕЙ ПО ЯРУСУ ({@code auth}, {@code market-data}), а ставка
 * приходит от ПЛОЩАДКИ через коннектор: коллаборатор другой, класс отказа
 * другой, и такт другой — комиссионный тир меняется на своей оси и
 * заметно реже реестров. Соединённые в один тик, они делили бы окно
 * исполнения и признак «уже бежит».
 *
 * <p><b>Живой сделке несвежая ставка риска не рвёт:</b> устаревшее
 * справочное число обесценивает сайзинг новых входов, и реакция на него —
 * мягкая ступень инструментного радиуса
 * (docs/models/domain/other/TradeFeeRate.md §«Запись и история»). Тик её
 * не ставит: писатель ступени — служба холдов, и она приезжает своим
 * компонентом.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeFeeRateSyncJob {

    private static final String JOB_NAME = "tradeFeeRateSyncJob";

    private final TradeFeeRateSyncService syncService;
    private final TradeFeeRateSyncProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${trade-fee-rate-sync.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        try {
            log.debug("Trade fee rates synchronized: {} observations", syncService.synchronize());
        } catch (RuntimeException failure) {
            log.error("Trade fee rate sync tick failed", failure);
        }
    }
}
