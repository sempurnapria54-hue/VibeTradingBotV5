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
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
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
 * подтверждён и рабочий блок молчит, проход отдаёт добычу ног транша
 * вместе с позицией; иначе транш стоял бы в отправленном входе бессрочно,
 * а живая экспозиция сделки не наблюдалась бы вовсе.
 *
 * <p><b>Встроенная защита потеряна — исход по обязательству, тот же, что у
 * подтверждённого входа.</b> Выходная проверка покрытия читается раньше
 * консолидации и раньше рабочего прохода и живостью ноги не гейтится:
 * перевыставленный вход несёт живую ногу и потерянную защиту снятой
 * (docs/components/TrancheEntrySubmittedHandler.md §«Выходные проверки»;
 * довод — .claude/decisions/submitted-entry-lost-attached-protection.md).
 *
 * <p><b>Закреплённая деталь входной проверкой этого обработчика не
 * является</b> (docs/components/TrancheEntrySubmittedHandler.md
 * §«Закреплённая деталь стратегии входной проверкой этого обработчика не
 * является»).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrancheEntrySubmittedHandler implements DealTrancheHandler {

    private final TrancheWorkPass workPass;
    private final TrancheActionDisposition disposition;
    private final SystemActionExecutor systemActionExecutor;
    private final ProtectionCoverageGate coverageGate;

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
        // Триггер выхода по статусу сделки, симметричный триггеру сопровождения: под
        // сворачиванием сделки живую входную ногу снимает дочистка обработчика
        // выхода — тем же порядком «сначала нога, потом экспозиция»
        // (docs/rules/exit-teardown-order.md). Без него ногу не снимал бы никто.
        // Налив снятой ноги тоже уводит в выход: прямой терминал — только у
        // транша без живой ноги и без операций, иначе экспозиция ушла бы мимо выхода.
        // Пустая причина сделки терминала не даёт — сделка уходит ошибочной
        // тропой (docs/lifecycles/DealTranche.md).
        if (isTrue(deal.isCollapsing())) {
            return isTrue(tranche.hasLiveEntryOrder()) || isTrue(tranche.hasEntryFill())
                    ? TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING)
                    : disposition.inheritedClose(deal);
        }
        if (isTrue(entryTerminalWithoutOperations(entry, tranche))) {
            return TrancheTransition.close(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        }
        if (isTrue(exposureGoneAfterFill(tranche, deal))) {
            log.info("Position of a filled entry is already closed, tranche goes to exit trancheId={}",
                    tranche.getId());
            return TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING);
        }
        // Выходная проверка «встроенная защита не потеряна» — раньше консолидации и
        // раньше рабочего прохода, без гейта живости ноги: на перевыставленном входе
        // живая нога есть, а потерянная защита принадлежит снятой. Живая частично
        // налитая нога нарушения не даёт — её покрытие отложенное
        // (docs/rules/live-risk-protection.md). Предикат читается по дому, а не
        // пересобирается здесь.
        if (isTrue(coverageGate.trancheViolated(dealContext, tranche))) {
            log.error("Coverage invariant violated on a submitted entry dealId={} trancheId={}",
                    deal.getId(), tranche.getId());
            return TrancheTransition.escalate(
                    HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED));
        }
        if (isTrue(entryConfirmed(entry, deal))) {
            return consolidateEntry(dealContext, tranche);
        }
        TrancheTransition work = workPass.run(dealContext, tranche);
        if (isTrue(workPass.spoke(work))) {
            return work;
        }
        return observeEntry(dealContext, tranche);
    }

    /**
     * Добыча налива входа — единственная тропа, которой он наблюдается:
     * строка исполнения создания ноги завершается на подтверждённой
     * ОТПРАВКЕ, и дальше живых строк у транша нет.
     *
     * <p><b>Состав заявок — тот же, что у наблюдения сопровождения</b>
     * ({@link DealTranche#observedOrders()}): живая нога и налитые носители
     * живой встроенной защиты. Носитель добывается и здесь: защита
     * терминальной ноги может остаться в постановке дольше одной добычи —
     * первая встреча терминала пустым разбором вывода о пропаже не даёт, — а
     * на перевыставленном входе снятая нога с наливом остаётся при транше,
     * чью финализацию читает новая живая нога. Без её добычи ожидание
     * защиты не гасло бы, и пропажу не наблюдал бы никто, пока вход не
     * подтвердится (.claude/decisions/protection-lost-needs-prior-terminal.md).
     *
     * <p><b>Ноги добываются ВМЕСТЕ с позицией, одним проходом, и позиция
     * последней.</b> Налив, наблюдённый без позиции, делает следующий проход
     * ложным дважды: сверка экспозиций траншей с нетто-размером живого
     * эпизода расходится (биржевая ступень на штатном входе), а «вход
     * налился, живого эпизода нет» читается как уже закрытая позиция. Заявок
     * к добыче нет — одна позиция.
     *
     * <p>Добыча едет наблюдением, а не работой: транш опрашивает налив
     * каждым проходом, и работу уровня сделки это занимать не должно
     * (docs/components/TrancheEntrySubmittedHandler.md §«Налив наблюдается
     * добычей»).
     */
    private TrancheTransition observeEntry(DealContext dealContext, DealTranche tranche) {
        TrancheTransition observation = TrancheTransition.stay();
        for (Order order : tranche.observedOrders()) {
            observation = observation.withObservation(
                    disposition.orderFetch(dealContext, order.getId()).orElse(null));
        }
        return observation.withObservation(disposition.positionFetch(dealContext).orElse(null));
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
