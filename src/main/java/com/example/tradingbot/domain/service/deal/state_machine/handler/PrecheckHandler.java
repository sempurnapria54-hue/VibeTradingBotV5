package com.example.tradingbot.domain.service.deal.state_machine.handler;

import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.deal.DealEvent;
import com.example.tradingbot.domain.model.market.MarketPhase;
import com.example.tradingbot.domain.model.strategy.Strategy;
import com.example.tradingbot.domain.model.strategy.StrategyDetails;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.deal.state_machine.TransitionResult;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PrecheckHandler implements StateHandler {

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PRECHECK;
    }

    @Override
    public void checkEntryInvariants(DealContext context) {
        Deal deal = context.getDeal();
        if (Objects.isNull(deal)) {
            throw new IllegalStateException("deal is null");
        }

        if (BooleanUtils.isFalse(Objects.equals(deal.getStatus(), Deal.Status.PRECHECK))) {
            throw new IllegalStateException("deal.status must be PRECHECK");
        }

        if (Objects.isNull(deal.getInstrumentId())) {
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
        if (Objects.equals(result.getNextStatus(), Deal.Status.ENTRY_SUBMITTED) && isPrecheckBlocked(context)) {
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
     * - для текущей фазы рынка найдены детали стратегии;
     * - торговля в этой фазе не запрещена;
     * - фаза рынка определена и не UNKNOWN;
     * - по инструменту нет активной позиции;
     * - входной ордер ещё не создан.
     */
    private boolean isPrecheckPassed(DealContext context) {
        Strategy strategy = context.getStrategy();
        StrategyDetails strategyDetails = context.getStrategyDetails();
        MarketPhase marketPhase = context.getMarketPhase();

        if (Objects.isNull(strategy) || strategy.isNotActive()) {
            return false;
        }

        if (Objects.isNull(strategyDetails)) {
            return false;
        }

        if (strategyDetails.isTradingDisabled()) {
            return false;
        }

        if (Objects.isNull(marketPhase) || Objects.isNull(marketPhase.getType()) || marketPhase.isUnknown()) {
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
        return BooleanUtils.isFalse(isPrecheckPassed(context));
    }
}
