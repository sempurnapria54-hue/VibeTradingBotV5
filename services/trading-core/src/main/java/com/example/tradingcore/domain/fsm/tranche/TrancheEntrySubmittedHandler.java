package com.example.tradingcore.domain.fsm.tranche;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Подтверждает, что входная заявка транша отправлена, и определяет,
 * появилась ли экспозиция
 * (docs/components/TrancheEntrySubmittedHandler.md).
 *
 * <p><b>Ребро в подтверждённый вход обработчик не пишет.</b> Он эмитит
 * команду консолидации входа, а само ребро пишет звено в одной
 * транзакции со своим завершением: обработчик ГЕЙТИТ эмиссию, а не
 * двигает статус (docs/processes/fsm-execution-layering.md).
 *
 * <p><b>Требование детали безусловно: восстановленный транш в этот статус
 * не приходит.</b> Входа он не отправлял, а ребро переоткрытия требует и
 * живой входной заявки, и разрешающего объявления — ни того, ни другого у
 * него нет (docs/lifecycles/DealTranche.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrancheEntrySubmittedHandler implements DealTrancheHandler {

    private final TrancheWorkPass workPass;
    private final TrancheActionDisposition disposition;
    private final SystemActionExecutor systemActionExecutor;

    @Override
    public DealTranche.Status handledStatus() {
        return DealTranche.Status.ENTRY_SUBMITTED;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        Order entry = tranche.entryOrder();
        if (isNull(entry) || isTrue(deal.unattributedLiveRisk()) || isTrue(deal.moreThanOneLiveEpisode())) {
            log.warn("Entry-submitted tranche in an impossible state dealId={} trancheId={} entryPresent={}",
                    deal.getId(), tranche.getId(), nonNull(entry));
            return TrancheTransition.escalate();
        }
        // Третий триггер выхода, симметричный триггеру сопровождения: под
        // сворачиванием сделки живую входную ногу снимает дочистка обработчика
        // выхода — тем же порядком «сначала нога, потом экспозиция»
        // (docs/rules/exit-teardown-order.md). Без него ногу не снимал бы никто.
        if (isTrue(deal.isCollapsing())) {
            return isTrue(tranche.hasLiveEntryOrder())
                    ? TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING)
                    : TrancheTransition.close(disposition.inheritedCloseReason(deal));
        }
        if (isTrue(entryTerminalWithoutOperations(entry, tranche))) {
            return TrancheTransition.close(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        }
        if (isTrue(exposureGoneAfterFill(tranche, deal))) {
            log.info("Position of a filled entry is already closed, tranche goes to exit trancheId={}",
                    tranche.getId());
            return TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING);
        }
        if (isTrue(entryConfirmed(entry, deal))) {
            return consolidateEntry(dealContext, tranche);
        }
        return workPass.run(dealContext, tranche);
    }

    /**
     * Консолидация входа — системное действие уровня транша: звено пишет
     * ребро в подтверждённый вход своей транзакцией.
     */
    private TrancheTransition consolidateEntry(DealContext dealContext, DealTranche tranche) {
        return systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, dealContext, tranche)
                .map(TrancheTransition::command)
                .orElseGet(TrancheTransition::stay);
    }

    /**
     * Вход терминален и операций по нему не было.
     *
     * <p><b>Чистый ноль здесь по причине закрытия не пишется.</b> Тропа
     * достижима из состояния, где заявка уже стояла на бирже и МОГЛА
     * частично исполниться, поэтому перед записью проверяется факт
     * операций: были — сделка идёт обычным путём финализации
     * (docs/rules/deal-without-operations.md).
     */
    private Boolean entryTerminalWithoutOperations(Order entry, DealTranche tranche) {
        return isFalse(entry.isLive()) && isFalse(tranche.hasEntryFill());
    }

    /**
     * Вход налился, а живого эпизода нет и живой входной ноги тоже: позиция
     * закрылась на бирже, и факты это объясняют. При активной сделке и
     * известной входной заявке это не аномалия, а восстановление.
     */
    private Boolean exposureGoneAfterFill(DealTranche tranche, Deal deal) {
        return isTrue(tranche.hasEntryFill())
                && isFalse(deal.hasLivePositionRisk())
                && isFalse(tranche.hasLiveEntryOrder());
    }

    /** Входная нога налита целиком, и живой эпизод по сделке есть. */
    private Boolean entryConfirmed(Order entry, Deal deal) {
        return isTrue(entry.isFilled()) && isTrue(deal.hasLivePositionRisk());
    }
}
