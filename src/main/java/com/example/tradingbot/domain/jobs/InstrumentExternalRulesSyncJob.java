package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.config.InstrumentExternalRulesSyncProperties;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalRulesExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.InstrumentExternalRulesMapper;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Обновляет актуальный навес внешних правил инструмента
 * (docs/components/InstrumentExternalRulesSyncJob.md): CRON-тик читает
 * спецификацию онбордженных инструментов по REST, нормализует через
 * снапшот и сохраняет JSONB-навесом на строке инструмента. Правила —
 * источник ограничений риск-преконтроля; меняются редко. Вне расписания
 * запускается асинхронно через {@link com.example.tradingbot.domain.jobs.facade.InstrumentExternalRulesSyncJobFacade}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentExternalRulesSyncJob {

    private static final Set<Instrument.Status> SYNCED_STATUSES = Set.of(
            Instrument.Status.SYNC,
            Instrument.Status.CANDLES_LOADING,
            Instrument.Status.ACTIVE);

    private static final String JOB_NAME = "instrumentExternalRulesSyncJob";

    private final InstrumentDataService instrumentDataService;
    private final InstrumentExternalRulesDataService rulesDataService;
    private final IntegrationService integrationService;
    private final InstrumentExternalRulesMapper rulesMapper;
    private final InstrumentExternalRulesSyncProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${instrument-external-rules-sync.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        List<Instrument> instruments = instrumentDataService.findByStatusIn(SYNCED_STATUSES);
        for (Instrument instrument : instruments) {
            try {
                syncRules(instrument);
            } catch (RuntimeException e) {
                log.error("Instrument external rules sync failed for {}", instrument.getId(), e);
            }
        }
    }

    private void syncRules(Instrument instrument) {
        InstrumentExternalRulesExternalSnapshot snapshot = integrationService.getInstrumentRules(
                instrument.getExternalId(), instrument.getExternalType());
        if (isNull(snapshot)) {
            log.warn("Instrument external rules not found on exchange: externalId={} id={}",
                    instrument.getExternalId(), instrument.getId());
            return;
        }
        InstrumentExternalRules rules = rulesMapper.snapshotToDomain(snapshot, instrument.getId());
        rulesDataService.save(instrument.getId(), rules);
    }
}
