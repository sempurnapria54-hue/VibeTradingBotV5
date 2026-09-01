package com.example.tradingbot.domain.deal.action;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Допустим ли шаг стратегии к применению прямо сейчас. Исполнимая форма —
 * docs/spec/strategy-walkthrough.json (величины stepPackageProgress,
 * stepAppliedOnEpisode, stepRetryGated, stepEligible); этот класс её
 * выражает, при расхождении верна спека.
 *
 * <p>Три конъюнкта, и каждый заведён против своей ошибки:
 * <ul>
 *   <li><b>условие шага</b> — порог выполнен рыночными данными;</li>
 *   <li><b>шаг не применён на эпизоде</b> — без него порог нижней ступени
 *       остаётся истинным и после применения, first-match выбирал бы её
 *       каждым проходом, а ступень трейлинга не исполнялась бы ни разу;</li>
 *   <li><b>повтор не гейтится стоящей ступенью</b> — иначе отказавшая
 *       надобность крутила бы петлю «FAILED → новая строка» без предела.</li>
 * </ul>
 *
 * <p><b>Область — эпизод ОБЪЕКТА шага.</b> У потраншевого шага объект —
 * его транш (строки отбираются парой «транш + номер эпизода»), у шага
 * агрегатной поверхности — сама сделка: транша у него нет ни одного,
 * переоткрытия у сделки не бывает, эпизод один. Дом правила —
 * docs/rules/strategy-step-once-per-episode.md §«Область признака —
 * эпизод объекта шага».
 */
@Slf4j
@Service
public class StrategyStepEligibility {

    /**
     * Состояние исполнения пакета шага на текущем эпизоде: три класса,
     * они же ось популяции правила.
     */
    public PackageProgress packageProgress(StrategyStep step, DealTranche tranche,
                                           List<DealActionState> actionStates) {
        int declared = declaredActionCount(step);
        long applied = appliedRows(step, tranche, actionStates);
        if (applied == 0) {
            return PackageProgress.НИ_ОДНОГО;
        }
        return applied < declared ? PackageProgress.ЧАСТЬ : PackageProgress.ВСЕ;
    }

    /**
     * Шаг применён на текущем эпизоде — то есть пакет его действий
     * ИСЧЕРПАН. Пустой пакет применённым шаг НЕ делает: у шага, не
     * объявляющего действий, правая часть неравенства равна нулю, и без
     * охраны признак был бы истинен с рождения — тогда форма выхода «шаг
     * EXIT несёт только условие» не проходила бы отбор ни одним проходом.
     */
    public Boolean appliedOnEpisode(StrategyStep step, DealTranche tranche,
                                    List<DealActionState> actionStates) {
        int declared = declaredActionCount(step);
        return declared > 0 && appliedRows(step, tranche, actionStates) >= declared;
    }

    /**
     * Гейт повтора отказавшей надобности: новая строка того же шага на том
     * же эпизоде не заводится, пока стоит ступень радиуса, поднятая
     * исчерпанием бюджета. Снятие ступени возобновляет надобность.
     */
    public Boolean retryGated(StrategyStep step, DealTranche tranche,
                              List<DealActionState> actionStates, Boolean standingRungOnActionRadius) {
        return isTrue(standingRungOnActionRadius)
                && rowsOfEpisode(step, tranche, actionStates)
                        .anyMatch(state -> DealActionStateStatus.FAILED.equals(state.getStatus()));
    }

    /** Шаг допустим к применению: конъюнкция трёх осей. */
    public Boolean eligible(StrategyStep step, DealTranche tranche, List<DealActionState> actionStates,
                            Boolean conditionMet, Boolean standingRungOnActionRadius) {
        if (isFalse(conditionMet)) {
            return false;
        }
        if (isTrue(appliedOnEpisode(step, tranche, actionStates))) {
            return false;
        }
        return isFalse(retryGated(step, tranche, actionStates, standingRungOnActionRadius));
    }

    private int declaredActionCount(StrategyStep step) {
        return emptyIfNull(step.getActions()).size();
    }

    /**
     * Строки, засчитываемые применёнными: SKIPPED не применялся, FAILED —
     * попытки исчерпаны, а надобность осталась (её судьбу решает гейт
     * повтора, а не признак применённости).
     */
    private long appliedRows(StrategyStep step, DealTranche tranche, List<DealActionState> actionStates) {
        return rowsOfEpisode(step, tranche, actionStates)
                .filter(state -> !DealActionStateStatus.FAILED.equals(state.getStatus()))
                .filter(state -> !DealActionStateStatus.SKIPPED.equals(state.getStatus()))
                .count();
    }

    /**
     * Строки исполнения действий этого шага на ТЕКУЩЕМ эпизоде объекта
     * шага. У агрегатного шага (транша нет) отбираются строки уровня
     * сделки — те, у которых транш пуст.
     */
    private java.util.stream.Stream<DealActionState> rowsOfEpisode(StrategyStep step, DealTranche tranche,
                                                                   List<DealActionState> actionStates) {
        List<Long> actionIds = emptyIfNull(step.getActions()).stream()
                .map(action -> action.getId())
                .filter(Objects::nonNull)
                .toList();
        return emptyIfNull(actionStates).stream()
                .filter(state -> actionIds.contains(state.getStrategyActionId()))
                .filter(state -> sameEpisode(state, tranche));
    }

    /** Строка принадлежит текущему эпизоду объекта шага. */
    private boolean sameEpisode(DealActionState state, DealTranche tranche) {
        if (isNull(tranche)) {
            // Агрегатный шаг: объект — сама сделка, эпизод у неё один,
            // и строка уровня сделки транша не несёт.
            return isNull(state.getDealTrancheId());
        }
        return Objects.equals(tranche.getId(), state.getDealTrancheId())
                && Objects.equals(tranche.getEpisodeSeq(), state.getTrancheEpisodeSeq());
    }

    /** Классы исполнения пакета шага на эпизоде — закрытый перечень. */
    public enum PackageProgress {

        /** Пакет не начат: отобранных строк на текущем эпизоде нет. */
        НИ_ОДНОГО,

        /** Пакет начат и не исчерпан. */
        ЧАСТЬ,

        /** Пакет исчерпан: строк не меньше, чем объявленных действий. */
        ВСЕ
    }
}
