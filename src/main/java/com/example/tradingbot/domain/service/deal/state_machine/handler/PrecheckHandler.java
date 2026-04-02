package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.model.market.MarketPhase;
import com.example.tradingbot.domain.model.strategy.Strategy;
import com.example.tradingbot.domain.model.strategy.StrategyDetails;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.ServiceCommandType;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.Objects.isNull;

@Service
public class PrecheckHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PRECHECK;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (deal == null) {
            throw new IllegalStateException("deal is null");
        }

        if (deal.getStatus() != Deal.Status.PRECHECK) {
            throw new IllegalStateException("deal.status must be PRECHECK");
        }

        if (deal.getInstrumentId() == null) {
            throw new IllegalStateException("deal.instrumentId is null");
        }
    }

    @Override
    public TransitionResult handle(DealContext context, DealEvent event) {
        return switch (event) {
            case CHECK_ENTRY_INVARIANTS -> TransitionResult.stay();

            case PROCESS, RETRY -> process();

            case CHECK_EXIT_INVARIANTS -> checkExitInvariantsInternal(context);

            case FAIL -> {
                context.getDeal()
                       .setCloseReason(Deal.CloseReason.EMERGENCY_STOP);
                yield TransitionResult.moveTo(Deal.Status.ERROR);
            }

            default -> TransitionResult.stay();
        };
    }

    @Override
    public void checkExitInvariants(DealContext context, TransitionResult result) {
        if (result.getNextStatus() == Deal.Status.ENTRY_SUBMITTED && isPrecheckBlocked(context)) {
            throw new IllegalStateException("Precheck transition to ENTRY_SUBMITTED is not allowed");
        }
    }

    private TransitionResult process() {
        return TransitionResult.stay(List.of(
                ServiceCommandType.REFRESH_POSITIONS,
                ServiceCommandType.REFRESH_BALANCE,
                ServiceCommandType.REFRESH_PENDING_ORDERS
        ));
    }

    private TransitionResult checkExitInvariantsInternal(DealContext context) {
        if (isPrecheckPassed(context)) {
            return TransitionResult.moveTo(Deal.Status.ENTRY_SUBMITTED);
        }

        if (isPrecheckBlocked(context)) {
            return TransitionResult.moveTo(Deal.Status.CLOSED);
        }

        return TransitionResult.stay();
    }

    /**
     * PRECHECK считается успешно пройденным, если:
     * - стратегия существует и активна;
     * - для текущей фазы рынка найдены активные детали стратегии;
     * - торговля в этой фазе не запрещена;
     * - фаза рынка определена и не UNKNOWN;
     * - по инструменту нет активной позиции;
     * - входной ордер ещё не создан.
     */
    private boolean isPrecheckPassed(DealContext context) {
        Strategy strategy = context.getStrategy();
        StrategyDetails strategyDetails = context.getStrategyDetails();
        MarketPhase marketPhase = context.getMarketPhase();

        if (isNull(strategy) || strategy.isNotActive()) {
            return false;
        }

        if (isNull(strategyDetails) || strategyDetails.isNotActive()) {
            return false;
        }

        if (strategyDetails.isTradingDisabled()) {
            return false;
        }

        if (isNull(marketPhase) || isNull(marketPhase.getType()) || marketPhase.isUnknown()) {
            return false;
        }

        if (context.hasActivePosition()) {
            return false;
        }

        if (context.hasEntryOrder()) {
            return false;
        }

        return true;
    }

    /**
     * PRECHECK считается заблокированным, если:
     * - контекст не прошёл precheck,
     * - и при этом это штатный сценарий "не входим", а не авария.
     * <p>
     * Пока что сюда относятся:
     * - неактивная стратегия;
     * - отсутствие деталей стратегии под фазу рынка;
     * - запрет торговли в данной фазе;
     * - UNKNOWN-фаза рынка;
     * - уже существующая позиция;
     * - уже созданный entry-ордер.
     */
    private boolean isPrecheckBlocked(DealContext context) {
        return !isPrecheckPassed(context);
    }
}