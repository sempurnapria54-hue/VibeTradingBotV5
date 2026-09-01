package com.example.tradingbot.domain.deal.handler;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmHandler;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.domain.service.market.MarketDataExpirationChecker;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.util.Constants;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса ACTIVE: ведёт сделку, пока её транши живут своими
 * жизненными циклами. Стадий входа и сопровождения здесь нет — они
 * принадлежат траншу; сделочный проход отвечает на вопросы уровня сделки.
 *
 * <p><b>Запрошено сворачивание</b> (проставлен {@code shutdownReason}) →
 * EXIT_PENDING: дальше вход не открывается ни одним траншем
 * (docs/spec/deal-tranche-lifecycle.json §riskCreatingUnderCollapse).
 *
 * <p><b>Все транши терминальны</b> → намерение закрыть сделку. НАМЕРЕНИЕ,
 * а не переход: право на терминал даёт гейт живого риска, и проверяет его
 * машина сделки, а не этот handler.
 *
 * <p><b>Данные шага устарели, и стратегия велела выходить</b> →
 * EXIT_PENDING с причиной {@code MARKET_DATA_EXPIRED}; велела сворачиваться
 * аварийно — сигнал жёсткой ступени инструмента. Писателем этой причины на
 * этом ребре объявлен именно этот handler
 * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
 *
 * <p>См. docs/components/DealActiveHandler.md.
 */
@Component
@RequiredArgsConstructor
public class DealActiveHandler implements DealFsmHandler {

    private final DealFsmSupport support;
    private final MarketDataExpirationChecker expirationChecker;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ACTIVE;
    }

    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (nonNull(deal.getShutdownReason())) {
            return Optional.of(DealTransition.transition(Deal.Status.EXIT_PENDING));
        }
        if (isTrue(deal.allTranchesTerminal())) {
            return Optional.of(DealTransition.transition(Deal.Status.CLOSED));
        }
        return expiredDataReaction(dealContext);
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        // Работа сделки в ACTIVE идёт траншами: собственной команды у
        // сделочного прохода на этом статусе нет.
        return DealTransition.stay();
    }

    /**
     * Реакция сделки на устаревание данных её шагов — только та её часть,
     * что выводит сделку из штатного ведения. Ждать и блокировать шаг
     * сделочному проходу нечем: обе реакции локальны шагу, и он к
     * применению просто не берётся.
     *
     * <p><b>Аварийная сильнее управляемой:</b> если один шаг велел
     * сворачиваться штатно, а другой — аварийно, побеждает аварийная.
     * Обратный порядок отдал бы снятие риска закрывающим действиям, которые
     * считаются по тем самым данным, которым нельзя доверять.
     */
    private Optional<DealTransition> expiredDataReaction(DealContext dealContext) {
        MarketDataExpiredAction reaction = strongestReaction(dealContext);
        if (isNull(reaction)) {
            return Optional.empty();
        }
        if (isTrue(reaction.isKillSwitch())) {
            return Optional.of(DealTransition.builder()
                    .holdSignal(HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED))
                    .build());
        }
        return Optional.of(DealTransition.builder()
                .nextStatus(Deal.Status.EXIT_PENDING)
                .shutdownReason(Deal.ShutdownReason.MARKET_DATA_EXPIRED)
                .build());
    }

    /**
     * Сильнейшая из реакций, объявленных шагами живых траншей на устаревшие
     * данные; {@code null} — выводить сделку из ведения не велел ни один
     * шаг (данные свежи либо реакция локальна шагу).
     *
     * <p>Область — шаги статуса КАЖДОГО живого транша, а не все шаги детали:
     * устаревание данных шага, до которого сделка ещё не дошла, сделку не
     * сворачивает.
     */
    private MarketDataExpiredAction strongestReaction(DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        MarketDataExpiredAction strongest = null;
        for (DealTranche tranche : dealContext.getDeal().liveTranches()) {
            for (StrategyStep step : support.stepsFor(dealContext, tranche.getStatus())) {
                MarketDataExpiredAction reaction = reactionOf(step, conditionContext, dealContext, tranche);
                if (isNull(reaction)) {
                    continue;
                }
                if (isTrue(reaction.isKillSwitch())) {
                    return reaction;
                }
                if (isTrue(reaction.isGracefulClose())) {
                    strongest = reaction;
                }
            }
        }
        return strongest;
    }

    /**
     * Реакция одного шага, либо {@code null} — реагировать нечем: данные
     * свежи, политики у шага нет или ветвь не назвала значения.
     *
     * <p>Ветвь читает операнды ОБЪЕКТА ШАГА, и объект здесь потраншевый.
     * Агрегатной поверхности (EXIT / FAIL_SAFE уровня сделки) проход не
     * обходит вовсе — её шаги ничем не помечены и никем не исполняются; это
     * названное ограничение, а не умолчание в пользу одной из ветвей.
     */
    private MarketDataExpiredAction reactionOf(StrategyStep step, ConditionEvaluationContext conditionContext,
                                               DealContext dealContext, DealTranche tranche) {
        StrategyMarketDataExpiredSetting setting = step.getMarketDataExpiredSetting();
        if (isNull(setting) || isTrue(expirationChecker.stepDataFresh(step, conditionContext))) {
            return null;
        }
        return setting.resolve(tranche.isRiskBearing(), tranche.isCovered(),
                dealContext.getDeal().stopUnresolved());
    }
}
