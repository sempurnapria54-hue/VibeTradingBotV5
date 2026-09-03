package com.example.tradingbot.domain.deal.handler;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmHandler;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.action.StrategyActionOrchestrator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.domain.service.market.MarketDataExpirationChecker;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.util.Constants;
import java.util.List;
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
 * <p><b>Сработал шаг УЗКОЙ АГРЕГАТНОЙ ПОВЕРХНОСТИ</b> ({@code EXIT} либо
 * {@code FAIL_SAFE} уровня сделки) → EXIT_PENDING; значение причины у них
 * разное — {@code STRATEGY_EXIT} и {@code RISK_CONTROL}. Пакет самого шага
 * исполняет уже обработчик координированного выхода: этот проход решает,
 * что сделка выходит, а не как.
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
    private final StrategyActionOrchestrator actionOrchestrator;

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
            return Optional.of(terminalOrCoordinatedExit(deal));
        }
        return exitDecision(dealContext);
    }

    /**
     * Работа сделки в ACTIVE идёт траншами; собственная команда у
     * сделочного прохода одна — очередное действие пакета шага узкой
     * агрегатной поверхности. Пакет исполняется ЗДЕСЬ, а не после
     * перехода: шаги уровня сделки сгруппированы статусом агрегата, и
     * после ребра в координированный выход набор этого статуса пуст.
     */
    @Override
    public DealTransition handle(DealContext dealContext) {
        return advanceDealLevelPackage(dealContext).orElseGet(DealTransition::stay);
    }

    /**
     * Все транши терминальны — но терминал сделки отсюда затребуется
     * <b>только на тропах закрытия БЕЗ входа</b>: там числа нет и считать
     * его не по чему. Разделитель — признак «позиция по сделке
     * наблюдалась» (docs/rules/deal-without-operations.md), а не
     * отсутствие живого риска в проходе: на тропе отменённого входа живая
     * заявка была и была снята.
     *
     * <p>Были операции — сделка идёт в координированный выход, и её
     * терминал затребует уже его обработчик: там число считает звено
     * расчёта, а штатный терминал его требует.
     */
    private DealTransition terminalOrCoordinatedExit(Deal deal) {
        return isTrue(deal.positionObserved())
                ? DealTransition.transition(Deal.Status.EXIT_PENDING)
                : DealTransition.transition(Deal.Status.CLOSED);
    }

    /**
     * Решение вывести сделку из штатного ведения. Порядок разбора не
     * произволен: <b>аварийная реакция сильнее управляемой</b>, поэтому
     * жёсткая ступень по устаревшим данным разбирается ПЕРВОЙ — иначе
     * управляемый выход отдал бы снятие риска закрывающим действиям,
     * которые считаются по тем самым данным, которым доверять нельзя.
     */
    private Optional<DealTransition> exitDecision(DealContext dealContext) {
        MarketDataExpiredAction reaction = strongestReaction(dealContext);
        if (nonNull(reaction) && isTrue(reaction.isKillSwitch())) {
            return Optional.of(DealTransition.builder()
                    .holdSignal(HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED))
                    .build());
        }
        Optional<DealTransition> byDeclaredStep = declaredExit(dealContext);
        if (byDeclaredStep.isPresent()) {
            return byDeclaredStep;
        }
        if (isNull(reaction)) {
            return Optional.empty();
        }
        return Optional.of(DealTransition.builder()
                .nextStatus(Deal.Status.EXIT_PENDING)
                .shutdownReason(Deal.ShutdownReason.MARKET_DATA_EXPIRED)
                .build());
    }

    /**
     * СРАБОТАВШИЙ шаг узкой агрегатной поверхности. Перечень типов закрыт
     * (docs/models/domain/aggregate/Strategy.md), и значение причины
     * закрытия берётся типом шага: {@code EXIT} — штатный выход по
     * стратегии, {@code FAIL_SAFE} — страховочное risk-control завершение.
     *
     * <p><b>«Сработал» читается по форме выхода, и форм две</b>
     * (docs/rules/no-partial-close.md §«Две законные формы полного
     * выхода»):
     *
     * <ul>
     *   <li><b>условная</b> — шаг несёт только условие: истинного условия
     *       довольно, эмитентом закрытия становится обработчик
     *       координированного выхода;</li>
     *   <li><b>явная</b> — шаг несёт действие выхода: ребро ждёт
     *       ИСЧЕРПАНИЯ пакета, потому что команды шлёт исполнитель
     *       действия, а после ребра набор шагов этого статуса пуст —
     *       перейдя раньше, сделка потеряла бы эмитента.</li>
     * </ul>
     */
    private Optional<DealTransition> declaredExit(DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        for (StrategyStep step : dealLevelExitSteps(dealContext)) {
            if (isFalse(support.conditionMet(step, conditionContext))) {
                continue;
            }
            if (isFalse(support.stepFired(step, dealContext, null))) {
                continue;
            }
            return Optional.of(DealTransition.builder()
                    .nextStatus(Deal.Status.EXIT_PENDING)
                    .closeReason(closeReasonOf(step))
                    .build());
        }
        return Optional.empty();
    }

    private Deal.CloseReason closeReasonOf(StrategyStep step) {
        return StrategyStepType.FAIL_SAFE.equals(step.getStepType())
                ? Deal.CloseReason.RISK_CONTROL
                : Deal.CloseReason.STRATEGY_EXIT;
    }

    /**
     * Сильнейшая из реакций, объявленных живыми шагами сделки на
     * устаревшие данные; {@code null} — выводить сделку из ведения не
     * велел ни один шаг (данные свежи либо реакция локальна шагу).
     *
     * <p>Область — <b>оба уровня объявления</b>: шаги статуса каждого
     * живого транша и шаги узкой агрегатной поверхности текущего статуса
     * сделки. Устаревание данных шага, до которого сделка ещё не дошла,
     * её не сворачивает.
     */
    private MarketDataExpiredAction strongestReaction(DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        MarketDataExpiredAction strongest = null;
        for (DealTranche tranche : dealContext.getDeal().liveTranches()) {
            for (StrategyStep step : support.stepsFor(dealContext, tranche)) {
                MarketDataExpiredAction reaction = reactionOf(step, conditionContext, dealContext,
                        tranche.isRiskBearing(), tranche.isCovered());
                if (nonNull(reaction) && isTrue(reaction.isKillSwitch())) {
                    return reaction;
                }
                strongest = strongerOf(strongest, reaction);
            }
        }
        Deal deal = dealContext.getDeal();
        for (StrategyStep step : support.dealLevelSteps(dealContext)) {
            MarketDataExpiredAction reaction = reactionOf(step, conditionContext, dealContext,
                    deal.anyTrancheRiskBearing(), deal.allTranchesCovered());
            if (nonNull(reaction) && isTrue(reaction.isKillSwitch())) {
                return reaction;
            }
            strongest = strongerOf(strongest, reaction);
        }
        return strongest;
    }

    private MarketDataExpiredAction strongerOf(MarketDataExpiredAction current, MarketDataExpiredAction candidate) {
        return nonNull(candidate) && isTrue(candidate.isGracefulClose()) ? candidate : current;
    }

    /**
     * Реакция одного шага, либо {@code null} — реагировать нечем: данные
     * свежи, политики у шага нет или ветвь не назвала значения.
     *
     * <p>Ветвь читает операнды ОБЪЕКТА ШАГА, и пару операндов выбирает
     * вызывающая сторона: у потраншевого шага — признаки его транша, у
     * шага агрегатной поверхности — агрегатные признаки сделки
     * (docs/rules/market-data-freshness.md §«Оси дискриминатора ветви»).
     */
    private MarketDataExpiredAction reactionOf(StrategyStep step, ConditionEvaluationContext conditionContext,
                                               DealContext dealContext, Boolean branchRiskBearing,
                                               Boolean branchCovered) {
        StrategyMarketDataExpiredSetting setting = step.getMarketDataExpiredSetting();
        if (isNull(setting) || isTrue(expirationChecker.stepDataFresh(step, conditionContext))) {
            return null;
        }
        return setting.resolve(branchRiskBearing, branchCovered, dealContext.getDeal().stopUnresolved());
    }

    /**
     * Очередное действие пакета агрегатного шага за проход.
     *
     * <p>Пусто — исполнять нечего: шага агрегатной поверхности нет,
     * условие не выполнено, пакет исчерпан либо форма выхода условная
     * (шаг несёт только условие). На последней команду закрытия шлёт сам
     * обработчик координированного выхода
     * (docs/rules/no-partial-close.md §«Две законные формы полного
     * выхода»).
     */
    private Optional<DealTransition> advanceDealLevelPackage(DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        for (StrategyStep step : dealLevelExitSteps(dealContext)) {
            if (isFalse(support.conditionMet(step, conditionContext))) {
                continue;
            }
            StrategyAction action = actionOrchestrator.nextAction(step, dealContext, null).orElse(null);
            if (isNull(action)) {
                continue;
            }
            DealActionState state = support.findOrCreateActionState(dealContext, null, action);
            return Optional.of(support.reactToPlan(
                    actionOrchestrator.plan(step, action, state, dealContext, null), dealContext));
        }
        return Optional.empty();
    }

    private List<StrategyStep> dealLevelExitSteps(DealContext dealContext) {
        return support.stepsOfType(support.dealLevelSteps(dealContext),
                StrategyStepType.EXIT, StrategyStepType.FAIL_SAFE);
    }
}
