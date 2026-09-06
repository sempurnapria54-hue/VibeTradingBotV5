package com.example.tradingcore.domain.fsm.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealHandler;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.StepSelection;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheCascadeResult;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ведёт сделку, пока она набирает и держит риск: прогоняет FSM каждого
 * нетерминального транша и решает выход всей сделки
 * (docs/components/DealActiveHandler.md).
 *
 * <p><b>Собственных заявок не ставит и не снимает:</b> вход, защита и
 * частичный выход — работа траншей. <b>Траншей не материализует</b> — их
 * заводит открытие сделки атомарно с ней.
 *
 * <p><b>Расхождение суммы экспозиций уводит сделку не решением
 * обработчика, а КАСКАДОМ биржевой ступени 2.</b> Живой риск есть, но не
 * приписан ни одному траншу — значит сломался наш собственный учёт, и
 * радиус доверия к нему неизвестен. Ступень названа домом инварианта
 * (docs/models/domain/aggregate/Deal.md §«Экспозиция сделки и сверка с
 * биржей»), обработчик её не выбирает.
 *
 * <p><b>У восстановленной сделки закреплённой детали нет, и это не отказ
 * проверки:</b> заводил её не выбор входа. Зато расхождение суммы
 * наступает у неё по построению — заявок у её транша нет, а живой эпизод
 * ненулевого размера есть, — и первый же проход ловит его названной выше
 * тропой.
 *
 * <p><b>Удаление определения сворачивает сделку, и проверка стои́т ПОСЛЕ
 * реакции на устаревание данных.</b> Удаление — управляемое
 * сворачивание, а устаревание может потребовать аварийного, и аварийная
 * реакция старше управляемой (docs/components/DealActiveHandler.md
 * §«Реакция на устаревание данных»). Обратный порядок отдал бы снятие
 * риска закрывающим действиям, которые считаются по данным, которым
 * доверять уже нельзя.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealActiveHandler implements DealHandler {

    private final TrancheCascade trancheCascade;
    private final StrategyStepSelector stepSelector;
    private final DealTerminalGate terminalGate;
    private final SystemActionExecutor systemActionExecutor;

    @Override
    public Deal.Status handledStatus() {
        return Deal.Status.ACTIVE;
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(terminalGate.exposureReconciled(deal.livePosition(), deal.getTranches()))) {
            log.error("Deal exposure does not reconcile with the exchange dealId={}", deal.getId());
            return DealTransition.requestRung(
                    HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED));
        }
        if (isTrue(deal.moreThanOneLiveEpisode()) || isTrue(deal.unattributedLiveRisk())) {
            log.warn("Unsafe live risk on an active deal dealId={}", deal.getId());
            return errorPath(dealContext);
        }
        TrancheCascadeResult cascade = trancheCascade.run(dealContext);
        DealTransition afterCascade = cascadeReaction(dealContext, cascade);
        if (nonNull(afterCascade)) {
            return afterCascade.withTrancheEdges(cascade.getEdges());
        }
        DealTransition dealLevel = dealLevelWork(dealContext);
        if (nonNull(dealLevel)) {
            return dealLevel.withTrancheEdges(cascade.getEdges());
        }
        if (isTrue(dealContext.strategyDeleted())) {
            log.info("Deal collapses because its strategy definition is deleted dealId={}", deal.getId());
            return DealTransition.collapse(Deal.ShutdownReason.STRATEGY_DELETED,
                            Deal.CloseReason.STRATEGY_EXIT)
                    .withTrancheEdges(cascade.getEdges());
        }
        return exitCheck(dealContext)
                .withTrancheEdges(cascade.getEdges())
                .withRung(cascade.getHoldSignal());
    }

    /**
     * Реакция на просьбы каскада; пусто — просьб нет и проход идёт дальше.
     *
     * <p><b>Порядок реакций — по цене ошибки.</b> Просьба увести ошибочной
     * тропой старше управляемого сворачивания: закрывающие действия
     * считаются по данным, которым в этот момент доверять уже нельзя.
     * Затребованная ступень статуса не двигает вовсе — её поднимает петля,
     * и активные сделки радиуса уводит её же шаг энфорсмента.
     */
    private DealTransition cascadeReaction(DealContext dealContext, TrancheCascadeResult cascade) {
        if (isTrue(cascade.getDealErrorRequested())) {
            return errorPath(dealContext).withRung(cascade.getHoldSignal());
        }
        if (nonNull(cascade.getShutdownRequested())) {
            return DealTransition.collapse(cascade.getShutdownRequested(), Deal.CloseReason.RISK_CONTROL)
                    .withRung(cascade.getHoldSignal());
        }
        if (isTrue(cascade.acted())) {
            return DealTransition.commands(cascade.getCommands()).withRung(cascade.getHoldSignal());
        }
        return null;
    }

    /**
     * Шаги УЗКОЙ агрегатной поверхности: выход и страховочный шаг.
     * Причина у них разная и объявлена типом шага
     * (docs/lifecycles/Deal.md).
     *
     * <p>Пусто — агрегатного шага на этом проходе нет. У восстановленной
     * сделки его нет никогда: шаги живут на детали, а детали у неё нет.
     */
    private DealTransition dealLevelWork(DealContext dealContext) {
        StepSelection selection = stepSelector.selectDealStep(dealContext);
        if (isTrue(selection.hasEscalation())) {
            return expiredData(selection);
        }
        if (isFalse(selection.hasStep())) {
            return null;
        }
        return DealTransition.collapse(null, closeReasonOf(selection.getStep()));
    }

    /**
     * Реакция агрегатного шага на устаревание его данных: управляемое
     * сворачивание — ребро с причиной устаревания, аварийная —
     * <b>сигнал</b> жёсткой ступени, и статуса проход при ней не двигает.
     *
     * <p>Увод сделки в ошибочное состояние здесь не пишется: активные
     * сделки радиуса уводит шаг энфорсмента петли, и дублировать его
     * значило бы завести второго писателя причине, у которой писатель уже
     * назван (docs/components/DealActiveHandler.md §«Реакция на
     * устаревание данных»).
     */
    private DealTransition expiredData(StepSelection selection) {
        if (isTrue(selection.getEscalation().isKillSwitch())) {
            return DealTransition.requestRung(
                    HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED));
        }
        return DealTransition.collapse(Deal.ShutdownReason.MARKET_DATA_EXPIRED,
                Deal.CloseReason.RISK_CONTROL);
    }

    /**
     * Выходные проверки: все транши терминальны.
     *
     * <p><b>Разделитель троп — признак «операций по сделке не было»</b>, а
     * не отсутствие живого риска в проходе: на тропе отменённого входа
     * живая заявка была и была снята
     * (docs/rules/deal-without-operations.md). Операций не было — терминал
     * затребуется прямо отсюда; были — сделка идёт в координированный
     * выход, и терминал затребует уже его обработчик.
     */
    private DealTransition exitCheck(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(deal.allTranchesTerminal())) {
            return DealTransition.stay();
        }
        if (isTrue(deal.positionObserved())) {
            return DealTransition.collapse(null, deal.closeReasonBySeniority());
        }
        return finalizeExit(dealContext, deal.closeReasonBySeniority());
    }

    /**
     * Терминал не вошедшей сделки: обработчик ГЕЙТИТ эмиссию действия
     * финализации выхода, а ребро ставит терминальное звено — статуса
     * обработчик не пишет.
     */
    private DealTransition finalizeExit(DealContext dealContext, Deal.CloseReason closeReason) {
        if (isNull(dealContext.getDeal().getCloseReason()) && nonNull(closeReason)) {
            dealContext.getDeal().setCloseReason(closeReason);
        }
        return systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, dealContext, null)
                .map(DealTransition.stay()::withCommand)
                .orElseGet(DealTransition::stay);
    }

    /** Ошибочная тропа: обработчик гейтит эмиссию звена, ребро пишет оно. */
    private DealTransition errorPath(DealContext dealContext) {
        return systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, dealContext, null)
                .map(DealTransition.stay()::withCommand)
                .orElseGet(DealTransition::stay);
    }

    /**
     * Причина закрытия по типу сработавшего агрегатного шага: перечень
     * закрыт двумя типами, и значения у них разные
     * (docs/lifecycles/Deal.md).
     */
    private Deal.CloseReason closeReasonOf(StrategyStep step) {
        return StrategyStepType.FAIL_SAFE.equals(step.getStepType())
                ? Deal.CloseReason.RISK_CONTROL
                : Deal.CloseReason.STRATEGY_EXIT;
    }
}
