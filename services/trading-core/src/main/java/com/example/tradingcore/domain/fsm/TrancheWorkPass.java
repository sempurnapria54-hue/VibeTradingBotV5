package com.example.tradingcore.domain.fsm;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Рабочий блок прохода транша, общий у всех его обработчиков: доиграть
 * живое исполнение, иначе отобрать шаг статуса и начать его действие.
 *
 * <p><b>Блок один на шесть статусов, потому что различает их только набор
 * объявленных шагов.</b> Он приезжает из объявления транша по его статусу
 * (docs/components/DealTrancheStateMachine.md §«Конструкция обработчика»);
 * копия этого блока у каждого обработчика расходилась бы с соседями при
 * первой же правке порядка «живое, потом новое».
 *
 * <p><b>Реакция на устаревание данных разводится по своей природе.</b>
 * Управляемое сворачивание просит у сделки ВЫХОД, аварийная реакция —
 * жёсткую ступень инструмента; подмена одного другим либо рвала бы по
 * рынку позицию под контролем, либо оставляла бы без реакции ту, что не
 * под ним (docs/rules/market-data-freshness.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrancheWorkPass {

    private final StrategyStepSelector stepSelector;
    private final StrategyWorkRunner workRunner;
    private final TrancheActionDisposition disposition;

    /** Один рабочий проход транша: команда действия либо пустой исход. */
    public TrancheTransition run(DealContext dealContext, DealTranche tranche) {
        Optional<ActionPlan> live = workRunner.advanceLive(dealContext, tranche);
        if (live.isPresent()) {
            return disposition.dispose(live.get(), dealContext, tranche);
        }
        StepSelection selection = stepSelector.selectTrancheStep(dealContext, tranche);
        if (isTrue(selection.hasEscalation())) {
            return expiredData(selection.getEscalation(), dealContext.getDeal(), tranche);
        }
        if (isFalse(selection.hasStep())) {
            return TrancheTransition.stay();
        }
        return disposition.dispose(workRunner.startNext(selection.getStep(), dealContext, tranche),
                dealContext, tranche);
    }

    /**
     * Рабочий проход что-то сказал: команда, просьба к сделке, ребро либо
     * затребованная ступень. Выходные проверки обработчика на такой исход
     * не накладываются — работа прохода уже занята.
     */
    public Boolean spoke(TrancheTransition transition) {
        return isTrue(transition.hasCommands())
                || isTrue(transition.getDealErrorRequested())
                || isTrue(transition.movesStatus())
                || nonNull(transition.getShutdownRequested())
                || nonNull(transition.getHoldSignal());
    }

    /**
     * Реакция на устаревание данных шага.
     *
     * <p><b>Просьба о сворачивании у сворачивающейся сделки уже исполнена</b>
     * и прохода не занимает: сказав её снова, транш каждым тиком запирал бы
     * свою выходную проверку, а подтверждённый вход идёт к выходу только
     * через неё (docs/rules/exit-teardown-order.md §«Окно сворачивания:
     * нового риска не берёт ни один транш»). Жёсткая ступень исполненной не
     * бывает — её затребование остаётся и под сворачиванием.
     */
    private TrancheTransition expiredData(MarketDataExpiredAction reaction, Deal deal, DealTranche tranche) {
        if (isTrue(reaction.isKillSwitch())) {
            log.warn("Step data expired, reaction applied trancheId={} reaction={}", tranche.getId(), reaction);
            return TrancheTransition.requestRung(
                    HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED));
        }
        if (isTrue(deal.isCollapsing())) {
            log.debug("Step data expired on a collapsing deal, shutdown already under way trancheId={}",
                    tranche.getId());
            return TrancheTransition.stay();
        }
        log.warn("Step data expired, reaction applied trancheId={} reaction={}", tranche.getId(), reaction);
        return TrancheTransition.requestShutdown(Deal.ShutdownReason.MARKET_DATA_EXPIRED);
    }
}
