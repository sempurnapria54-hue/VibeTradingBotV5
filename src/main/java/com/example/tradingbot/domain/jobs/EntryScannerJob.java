package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.EntryScannerProperties;
import com.example.tradingbot.domain.deal.DealOpeningService;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.domain.deal.MarketConditionContextFactory;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.service.market.MarketPhaseService;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.service.market.condition.StrategyConditionEvaluator;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ищет возможность создать новую сделку. По активным инструментам:
 * safety-холд (TRADE_BLOCKED инструмента исключён выборкой ACTIVE; биржа в
 * TRADE_BLOCKED → каскадно пропускаем все её инструменты, L4) → фаза рынка
 * (MarketPhaseService) → pinned StrategyDetail по типу фазы → phaseEntryPolicy
 * (NO_TRADE / несоответствие фазе → стоп) → gatekeeper (нет активной сделки по
 * инструменту) → ENTRY/GRID_ENTRY condition; при выполнении — DealOpeningService.
 * Ордера не выставляет и FSM не запускает. Concurrency — in-memory
 * {@link JobExecutionGuard} (создание дубля предотвращает gatekeeper). См.
 * docs/components/EntryScannerJob.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryScannerJob {

    private static final String JOB_NAME = "entryScannerJob";

    private final EntryScannerProperties properties;
    private final JobExecutionGuard executionGuard;
    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final StrategyDataService strategyDataService;
    private final MarketPhaseService marketPhaseService;
    private final MarketConditionContextFactory conditionContextFactory;
    private final StrategyConditionEvaluator conditionEvaluator;
    private final DealDataService dealDataService;
    private final DealOpeningService dealOpeningService;
    private final IntegrationService integrationService;

    @Scheduled(cron = "${entry-scanner.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        Set<Long> blockedExchangeIds = new HashSet<>(
                exchangeDataService.findIdsByStatus(Exchange.Status.TRADE_BLOCKED));
        for (Instrument instrument : instrumentDataService.findByStatus(Instrument.Status.ACTIVE)) {
            if (blockedExchangeIds.contains(instrument.getExchangeId())) {
                // Каскад L4: биржа в TRADE_BLOCKED → входы по всем её инструментам заблокированы.
                continue;
            }
            try {
                scanInstrument(instrument);
            } catch (RuntimeException e) {
                log.error("Entry scan failed instrumentId={}", instrument.getId(), e);
            }
        }
    }

    private void scanInstrument(Instrument instrument) {
        if (isTrue(dealDataService.existsActiveByInstrumentId(instrument.getId()))) {
            return;
        }
        Strategy strategy = strategyDataService.findActiveByInstrumentIdWithTree(instrument.getId()).orElse(null);
        if (isNull(strategy)) {
            return;
        }
        MarketPhase phase = marketPhaseService.getCurrentPhase(instrument, strategy).orElse(null);
        if (isNull(phase)) {
            return;
        }
        StrategyDetail detail = strategy.detailForPhase(phase.getType()).orElse(null);
        if (isNull(detail) || isFalse(detail.allowsEntryFor(phase.getType()))) {
            return;
        }
        evaluateEntry(instrument, detail, phase.getType());
    }

    private void evaluateEntry(Instrument instrument, StrategyDetail detail, MarketPhase.Type phaseType) {
        List<StrategyStep> entrySteps = detail.entrySteps();
        if (isEmpty(entrySteps)) {
            return;
        }
        ConditionEvaluationContext conditionContext = conditionContextFactory.build(instrument);
        for (StrategyStep step : entrySteps) {
            if (isNotTrue(conditionEvaluator.evaluate(step.getCondition(), conditionContext))) {
                continue;
            }
            StrategyOrderAction entryAction = step.firstOrderAction().orElse(null);
            if (isNull(entryAction)) {
                continue;
            }
            // Фаза уже вычислена к этому моменту, и второй раз её никто не
            // считает; биржевой момент создания добывается ЗДЕСЬ — сервис
            // создания на биржу за ним не ходит. Деталь уезжает целиком:
            // транши материализуются по её объявлениям, и тип входного
            // шага читается на объявлении, а не приходит параметром.
            dealOpeningService.openDeal(instrument.getId(), detail, entryAction.getDirection(),
                    phaseType, integrationService.getServerTime());
            return;
        }
    }
}
