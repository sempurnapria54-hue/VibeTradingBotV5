package com.example.tradingbot.domain.deal;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Атомарно создаёт {@link Deal} в статусе PRECHECK по уже выбранным entry-
 * данным (instrumentId, strategyDetailId, direction, entryReason,
 * entryStepType). Финальная защитная проверка gatekeeper'а: по инструменту
 * нет активной сделки (иначе вход не открывается — инвариант «одна сделка
 * на инструмент»). Торговое решение о входе принял EntryScannerJob; FSM
 * здесь не запускается. См. docs/components/DealOpeningService.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealOpeningService {

    private final DealDataService dealDataService;

    @Transactional
    public Optional<Deal> openDeal(Long instrumentId, Long strategyDetailId, StrategyTradeDirection direction,
                                   Deal.EntryReason entryReason, Deal.EntryStepType entryStepType) {
        if (isTrue(dealDataService.existsActiveByInstrumentId(instrumentId))) {
            log.debug("Active deal already exists for instrument {} — entry skipped", instrumentId);
            return Optional.empty();
        }
        Deal deal = new Deal();
        deal.setInternalId(ClientIdGenerator.generate());
        deal.setInstrumentId(instrumentId);
        deal.setStrategyDetailId(strategyDetailId);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setDirection(direction);
        deal.setEntryReason(entryReason);
        deal.setEntryStepType(entryStepType);
        return Optional.of(dealDataService.save(deal));
    }
}
