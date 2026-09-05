package com.example.tradingcore.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingcore.config.ProjectionSyncProperties;
import com.example.tradingcore.domain.service.RegistryProjectionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Тик синка проекций чужих реестров: реестр счетов у {@code auth},
 * каталог инструментов у {@code market-data}.
 *
 * <p><b>Это единственный писатель проекционных колонок</b>
 * (docs/architecture/data-ownership.md §«Копии чужих данных»). Целевой
 * писатель — потребитель события владельца; пока производителя нет,
 * проекцию наполняет синхронное чтение, и форма таблицы при смене
 * писателя не меняется.
 *
 * <p><b>Момент снимка берётся ОДИН на тик, а не на строку.</b> Он
 * отвечает на вопрос «когда мы в последний раз спрашивали владельца», и
 * дробить его по строкам значило бы мерить длительность собственного
 * прохода, а не возраст данных.
 *
 * <p><b>Половины тика независимы.</b> Недоступность одного владельца не
 * отменяет синка у другого: проекции обслуживают разные решения, и
 * связывать их отказы значило бы делать перезапуск {@code auth} причиной
 * устаревания каталога.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryProjectionJob {

    private static final String JOB_NAME = "registryProjectionJob";

    private final RegistryProjectionService projectionService;
    private final ProjectionSyncProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${projection-sync.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        OffsetDateTime projectedAt = OffsetDateTime.now(ZoneOffset.UTC);
        syncAccounts(projectedAt);
        syncInstruments(projectedAt);
    }

    private void syncAccounts(OffsetDateTime projectedAt) {
        try {
            log.debug("Exchange account projection synchronized: {} rows",
                    projectionService.synchronizeExchangeAccounts(projectedAt));
        } catch (RuntimeException e) {
            log.error("Exchange account projection sync failed", e);
        }
    }

    private void syncInstruments(OffsetDateTime projectedAt) {
        try {
            log.debug("Instrument projection synchronized: {} rows",
                    projectionService.synchronizeInstruments(projectedAt));
        } catch (RuntimeException e) {
            log.error("Instrument projection sync failed", e);
        }
    }
}
