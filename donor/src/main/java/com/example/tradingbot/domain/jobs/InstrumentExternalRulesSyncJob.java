package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.config.InstrumentExternalRulesSyncProperties;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalRulesExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.InstrumentExternalRulesMapper;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.domain.model.other.external_snapshot.TradeFeeRateExternalSnapshot;
import com.example.tradingbot.mapping.TradeFeeRateMapper;
import com.example.tradingbot.persistence.service.TradeFeeRateDataService;
import java.util.List;
import java.util.Map;
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
    private final TradeFeeRateDataService tradeFeeRateDataService;
    private final TradeFeeRateMapper tradeFeeRateMapper;

    @Scheduled(cron = "${instrument-external-rules-sync.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        List<Instrument> instruments = instrumentDataService.findByStatusIn(SYNCED_STATUSES);
        syncFeeRates(instruments);
        for (Instrument instrument : instruments) {
            try {
                syncRules(instrument);
            } catch (RuntimeException e) {
                log.error("Instrument external rules sync failed for {}", instrument.getId(), e);
            }
        }
    }

    /**
     * Ставки комиссий — ОДИН вызов на тик по типу инструмента, не по
     * инструменту: ставка есть атрибут комиссионной группы счёта, и N
     * вызовов на N инструментов размножили бы одно и то же значение
     * (docs/integrations/okx/contracts/trade-fee.md). Тип берётся у
     * онбордженных инструментов — контур фазы 1 SWAP-only, но перечень
     * читается из данных, а не хардкодится.
     */
    private void syncFeeRates(List<Instrument> instruments) {
        Map<Long, Set<String>> typesByExchange = instruments.stream()
                .filter(instrument -> nonNull(instrument.getExchangeId())
                        && isNotBlank(instrument.getExternalType()))
                .collect(groupingBy(Instrument::getExchangeId,
                        mapping(Instrument::getExternalType, toSet())));
        typesByExchange.forEach((exchangeId, instrumentTypes) -> instrumentTypes.forEach(instrumentType -> {
            try {
                recordFeeRates(exchangeId, instrumentType);
            } catch (RuntimeException e) {
                log.error("Trade fee rates sync failed for exchange={} instType={}", exchangeId, instrumentType, e);
            }
        }));
    }

    private void recordFeeRates(Long exchangeId, String instrumentType) {
        List<TradeFeeRateExternalSnapshot> snapshots = integrationService.getTradeFeeRates(instrumentType);
        if (isEmpty(snapshots)) {
            log.warn("Trade fee rates not returned for instType={}", instrumentType);
            return;
        }
        for (TradeFeeRateExternalSnapshot snapshot : snapshots) {
            tradeFeeRateDataService.record(tradeFeeRateMapper.snapshotToDomain(snapshot, exchangeId));
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
