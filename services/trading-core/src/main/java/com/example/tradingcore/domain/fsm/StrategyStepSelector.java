package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Отбирает шаг, применимый на этом проходе: first-match по объявленному
 * порядку с тремя охранами — гейт свежести данных, признак применённости
 * пакета и гейт повтора отказавшей надобности. Исполнимая форма —
 * docs/spec/strategy-walkthrough.json (величина {@code stepEligible}) и
 * docs/spec/market-data-freshness.json (величина {@code reaction}).
 *
 * <p><b>Отбор общий на оба уровня объявления.</b> Потраншевые шаги живут
 * на объявлении транша, агрегатные — на детали; охраны у них одни и те
 * же, и второй экземпляр отбора разошёлся бы с первым ровно там, где
 * закрывается позиция (docs/rules/strategy-step-once-per-episode.md
 * §«Область признака — эпизод объекта шага»).
 *
 * <p><b>Гейт свежести режет только сам шаг.</b> Род действия
 * {@code DATA_DEPENDENT} — это то, что объявлено пакетом шага; добыча,
 * дочистка и safety идут мимо отбора вовсе и блокировкой не задеваются
 * (docs/spec/market-data-freshness.json, величина {@code actionBlocked}).
 *
 * <p><b>Решений о статусе не принимает и команд не строит.</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyStepSelector {

    private final StrategyConditionEvaluator conditionEvaluator;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;

    /**
     * Шаг объявления транша, применимый в его текущем статусе.
     *
     * <p>У восстановленного транша объявления нет, и набор шагов пуст —
     * это прямое следствие пустого объявления, а не молчаливое умолчание
     * (docs/components/TrancheManagingHandler.md).
     */
    public StepSelection selectTrancheStep(DealContext dealContext, DealTranche tranche) {
        StrategyTranche declaration = dealContext.declarationOf(tranche);
        if (isNull(declaration) || isNull(declaration.getStepsByStatus())) {
            return StepSelection.none();
        }
        return select(declaration.getStepsByStatus().get(tranche.getStatus()), dealContext, tranche);
    }

    /**
     * Шаг узкой агрегатной поверхности детали, применимый в текущем
     * статусе сделки. Транша у такого шага нет ни одного, и область его
     * признака применённости вырождается в саму сделку.
     */
    public StepSelection selectDealStep(DealContext dealContext) {
        if (isNull(dealContext.getStrategyDetail())) {
            return StepSelection.none();
        }
        return select(dealContext.getStrategyDetail().dealLevelSteps(dealContext.getDeal().getStatus()),
                dealContext, null);
    }

    private StepSelection select(List<StrategyStep> steps, DealContext dealContext, DealTranche tranche) {
        for (StrategyStep step : emptyIfNull(steps)) {
            if (isTrue(appliedOnEpisode(step, dealContext, tranche))
                    || isTrue(retryGated(step, dealContext, tranche))) {
                continue;
            }
            MarketDataExpiredAction reaction = expiredReaction(step, dealContext, tranche);
            if (nonNull(reaction)) {
                if (isTrue(reaction.isGracefulClose()) || isTrue(reaction.isKillSwitch())) {
                    return StepSelection.escalated(reaction);
                }
                continue;
            }
            if (isTrue(conditionEvaluator.evaluate(step.getCondition(), dealContext.conditionContext(tranche)))) {
                return StepSelection.of(step);
            }
        }
        return StepSelection.none();
    }

    // ------------------------------------------------------------------
    // Свежесть данных шага
    // ------------------------------------------------------------------

    /**
     * Реакция шага на недоступность его рыночных операндов; пусто —
     * данные шагу доступны либо он их не спрашивает вовсе.
     *
     * <p><b>Пустая настройка реакции читается как блокировка шага.</b>
     * Настройка обязательна у каждого шага
     * (docs/models/domain/aggregate/Strategy.md), и её отсутствие —
     * состояние, которого модель не производит; разрешающее прочтение
     * пустоты исполнило бы шаг на недоступных данных.
     */
    private MarketDataExpiredAction expiredReaction(StrategyStep step, DealContext dealContext,
                                                    DealTranche tranche) {
        if (isFalse(dataDependent(step)) || isTrue(featuresCoverStep(step, dealContext))) {
            return null;
        }
        StrategyMarketDataExpiredSetting setting = step.getMarketDataExpiredSetting();
        if (isNull(setting)) {
            log.warn("Step declares no market-data-expired setting, step blocked stepId={}", step.getId());
            return MarketDataExpiredAction.BLOCK_STEP;
        }
        return setting.resolve(branchRiskBearing(dealContext, tranche), branchCovered(dealContext, tranche),
                dealContext.getDeal().stopUnresolved());
    }

    private Boolean dataDependent(StrategyStep step) {
        return nonNull(step.getCondition()) && isTrue(step.getCondition().readsMarketData());
    }

    private Boolean featuresCoverStep(StrategyStep step, DealContext dealContext) {
        return nonNull(dealContext.getMarketFeatures())
                && isTrue(dealContext.getMarketFeatures().covers(step.getCondition()));
    }

    /**
     * Живой риск ОБЪЕКТА ШАГА: у потраншевого — его транша, у шага
     * агрегатной поверхности — самой сделки. Разведение обязательно:
     * траншевые операнды на агрегатном шаге не определены, и ветвь пары
     * реакций не резолвилась бы ничем
     * (docs/rules/market-data-freshness.md §«Оси дискриминатора ветви»).
     */
    private Boolean branchRiskBearing(DealContext dealContext, DealTranche tranche) {
        return isNull(tranche) ? dealContext.getDeal().anyTrancheRiskBearing() : tranche.isRiskBearing();
    }

    /** Покрытие ОБЪЕКТА ШАГА — по тому же разведению уровня. */
    private Boolean branchCovered(DealContext dealContext, DealTranche tranche) {
        return isNull(tranche) ? dealContext.getDeal().allTranchesCovered() : tranche.isCovered();
    }

    // ------------------------------------------------------------------
    // Однократность шага на эпизоде и гейт повтора
    // ------------------------------------------------------------------

    /**
     * Шаг применён на текущем эпизоде: пакет его действий ИСЧЕРПАН.
     *
     * <p><b>Охрана пустого пакета стои́т первой.</b> У шага без объявленных
     * действий правая часть неравенства равна нулю, и без охраны признак
     * истинен с рождения — то есть шаг не допустим никогда. Между тем
     * пустой пакет законен: шаг полного выхода несёт только условие
     * (docs/rules/no-partial-close.md), и гасит его переход статуса, а не
     * признак применённости.
     */
    private Boolean appliedOnEpisode(StrategyStep step, DealContext dealContext, DealTranche tranche) {
        int declared = emptyIfNull(step.getActions()).size();
        return declared > 0 && applied(step, dealContext, tranche) >= declared;
    }

    private int applied(StrategyStep step, DealContext dealContext, DealTranche tranche) {
        return (int) dealContext.stepActionStates(step, tranche).stream()
                .filter(state -> isFalse(DealActionStateStatus.SKIPPED.equals(state.getStatus())))
                .filter(state -> isFalse(DealActionStateStatus.FAILED.equals(state.getStatus())))
                .count();
    }

    /**
     * Повтор отказавшей надобности заморожен стоящей ступенью пары
     * «счёт, инструмент».
     *
     * <p>Связь «строка → причина ступени» не хранится: ступень читается по
     * паре, и чужая ступень той же пары замораживает повтор наравне со
     * своей. Биржевой радиус в область не входит — под мягкой биржевой
     * ступенью живые сделки сопровождаются полностью
     * (docs/rules/exchange-hold.md).
     */
    private Boolean retryGated(StrategyStep step, DealContext dealContext, DealTranche tranche) {
        List<DealActionState> rows = dealContext.stepActionStates(step, tranche);
        if (isEmpty(rows)) {
            return false;
        }
        boolean failed = rows.stream()
                .anyMatch(state -> DealActionStateStatus.FAILED.equals(state.getStatus()));
        if (isFalse(failed)) {
            return false;
        }
        AccountInstrumentState pairState = accountInstrumentStateDataService.getRequiredByPair(
                dealContext.getDeal().getExchangeAccountId(), dealContext.getDeal().getInstrumentId());
        return pairState.hasStandingSafetyRung();
    }
}
