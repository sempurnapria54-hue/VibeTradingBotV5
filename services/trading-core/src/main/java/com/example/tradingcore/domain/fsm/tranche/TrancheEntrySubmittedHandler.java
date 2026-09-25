package com.example.tradingcore.domain.fsm.tranche;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
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
 * <p><b>Налив входа наблюдает этот обработчик.</b> Пока вход не
 * подтверждён и рабочий блок молчит, проход отдаёт добычу ноги, а по её
 * наливу — позиции; иначе транш стоял бы в отправленном входе бессрочно,
 * а живая экспозиция сделки не наблюдалась бы вовсе.
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
        // Налив снятой ноги тоже уводит в выход: прямой терминал — только у
        // транша без живой ноги и без операций, иначе экспозиция ушла бы мимо выхода.
        if (isTrue(deal.isCollapsing())) {
            return isTrue(tranche.hasLiveEntryOrder()) || isTrue(tranche.hasEntryFill())
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
        TrancheTransition work = workPass.run(dealContext, tranche);
        if (isTrue(workPass.spoke(work))) {
            return work;
        }
        return observeEntry(dealContext, entry);
    }

    /**
     * Добыча налива входа — единственная тропа, которой он наблюдается:
     * строка исполнения создания ноги завершается на подтверждённой
     * ОТПРАВКЕ, и дальше живых строк у транша нет.
     *
     * <p><b>Живая нога добывается ВМЕСТЕ с позицией, одним проходом.</b>
     * Налив, наблюдённый без позиции, делает следующий проход ложным
     * дважды: сверка экспозиций траншей с нетто-размером живого эпизода
     * расходится (биржевая ступень на штатном входе), а «вход налился,
     * живого эпизода нет» читается как уже закрытая позиция. Налитая нога
     * — одна позиция: её живой эпизод и есть второй операнд подтверждённого
     * входа.
     *
     * <p>Добыча едет наблюдением, а не работой: транш опрашивает налив
     * каждым проходом, и работу уровня сделки это занимать не должно
     * (docs/components/TrancheEntrySubmittedHandler.md §«Налив наблюдается
     * добычей»).
     */
    private TrancheTransition observeEntry(DealContext dealContext, Order entry) {
        ServiceCommand position = disposition.positionFetch(dealContext).orElse(null);
        if (isFalse(entry.isLive())) {
            return TrancheTransition.observe(position);
        }
        return TrancheTransition.observe(disposition.orderFetch(dealContext, entry.getId()).orElse(null))
                .withObservation(position);
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

    /**
     * Налив входной ноги окончателен — она налита целиком либо снята после
     * частичного налива, — и живой эпизод по сделке есть. Снятая частично
     * налитая нога входом с окончательным размером и является: иначе транш
     * добывал бы её каждым проходом бессрочно.
     */
    private Boolean entryConfirmed(Order entry, Deal deal) {
        return isTrue(entry.hasFinalFill()) && isTrue(deal.hasLivePositionRisk());
    }
}
