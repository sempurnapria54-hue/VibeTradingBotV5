package com.example.tradingbot.domain.deal.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает правило допустимости шага с его исполнимой спецификацией
 * (docs/spec/strategy-walkthrough.json §stepEligible и соседи).
 */
class StrategyStepEligibilityTest {

    private final StrategyStepEligibility eligibility = new StrategyStepEligibility();

    @Test
    @DisplayName("Пустой пакет применённым шаг не делает: форма «шаг EXIT несёт только условие» допустима")
    void emptyPackageDoesNotMarkStepApplied() {
        StrategyStep step = step();
        DealTranche tranche = tranche();

        assertFalse(eligibility.appliedOnEpisode(step, tranche, List.of()));
        assertTrue(eligibility.eligible(step, tranche, List.of(), true, false));
        // Ложное условие допустимости не даёт и на пустом пакете.
        assertFalse(eligibility.eligible(step, tranche, List.of(), false, false));
    }

    @Test
    @DisplayName("Применённым шаг делает только исчерпанный пакет, а не строка первого действия")
    void onlyExhaustedPackageMarksApplied() {
        StrategyStep step = step(1L, 2L);
        DealTranche tranche = tranche();

        List<DealActionState> partial = List.of(row(1L, DealActionStateStatus.COMPLETED));
        assertEquals(StrategyStepEligibility.PackageProgress.ЧАСТЬ,
                eligibility.packageProgress(step, tranche, partial));
        assertFalse(eligibility.appliedOnEpisode(step, tranche, partial));
        assertTrue(eligibility.eligible(step, tranche, partial, true, false));

        List<DealActionState> full = List.of(row(1L, DealActionStateStatus.COMPLETED),
                row(2L, DealActionStateStatus.COMPLETED));
        assertEquals(StrategyStepEligibility.PackageProgress.ВСЕ,
                eligibility.packageProgress(step, tranche, full));
        assertTrue(eligibility.appliedOnEpisode(step, tranche, full));
        assertFalse(eligibility.eligible(step, tranche, full, true, false));
    }

    @Test
    @DisplayName("Отказавшая строка гейтит повтор только при стоящей ступени")
    void failedRowGatesRepeatOnlyUnderStandingRung() {
        StrategyStep step = step(1L);
        DealTranche tranche = tranche();
        List<DealActionState> failed = List.of(row(1L, DealActionStateStatus.FAILED));

        // FAILED не применялся: пакет не начат.
        assertEquals(StrategyStepEligibility.PackageProgress.НИ_ОДНОГО,
                eligibility.packageProgress(step, tranche, failed));
        // Ступень не стои́т — надобность повторяема.
        assertFalse(eligibility.retryGated(step, tranche, failed, false));
        assertTrue(eligibility.eligible(step, tranche, failed, true, false));
        // Ступень стои́т — шаг ждёт её снятия.
        assertTrue(eligibility.retryGated(step, tranche, failed, true));
        assertFalse(eligibility.eligible(step, tranche, failed, true, true));
    }

    @Test
    @DisplayName("Строки прошлого эпизода в отбор не попадают — переоткрытие идёт тем же траншем")
    void rowsOfPreviousEpisodeAreNotCounted() {
        StrategyStep step = step(1L);
        DealTranche tranche = tranche();
        DealActionState previous = row(1L, DealActionStateStatus.COMPLETED);
        previous.setTrancheEpisodeSeq(1);

        assertFalse(eligibility.appliedOnEpisode(step, tranche, List.of(previous)));
        assertTrue(eligibility.eligible(step, tranche, List.of(previous), true, false));
    }

    @Test
    @DisplayName("Агрегатный шаг отбирает строки уровня сделки: правило покрывает и его")
    void dealLevelStepUsesDealLevelRows() {
        StrategyStep step = step(1L);
        DealActionState dealLevel = row(1L, DealActionStateStatus.COMPLETED);
        dealLevel.setDealTrancheId(null);
        dealLevel.setTrancheEpisodeSeq(null);

        assertTrue(eligibility.appliedOnEpisode(step, null, List.of(dealLevel)));
        assertFalse(eligibility.eligible(step, null, List.of(dealLevel), true, false));

        DealActionState failedDealLevel = row(1L, DealActionStateStatus.FAILED);
        failedDealLevel.setDealTrancheId(null);
        failedDealLevel.setTrancheEpisodeSeq(null);
        assertTrue(eligibility.retryGated(step, null, List.of(failedDealLevel), true));
    }

    private StrategyStep step(Long... actionIds) {
        StrategyStep step = new StrategyStep();
        List<StrategyAction> actions = new ArrayList<>();
        for (Long id : actionIds) {
            StrategyOrderAction action = new StrategyOrderAction();
            action.setId(id);
            actions.add(action);
        }
        step.setActions(actions);
        return step;
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(7L);
        tranche.setEpisodeSeq(2);
        return tranche;
    }

    private DealActionState row(Long actionId, DealActionStateStatus status) {
        DealActionState state = new DealActionState();
        state.setStrategyActionId(actionId);
        state.setStatus(status);
        state.setDealTrancheId(7L);
        state.setTrancheEpisodeSeq(2);
        return state;
    }
}
