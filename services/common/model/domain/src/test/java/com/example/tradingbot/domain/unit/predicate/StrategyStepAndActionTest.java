package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyLevelSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPlacementRole;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Шаг, объявление, действие: лестница защит и сторона заявки — группа
 * `U16` документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/rules/live-risk-protection.md §«Размер ступени лестницы» и §«Что
 * считается защитой»; docs/rules/strategy-validation.md;
 * docs/models/domain/core/Order.md §«Енум `Side`»).
 *
 * <p><b>Базовая сборка:</b> шаг с типом и коллекцией действий трёх
 * классов; объявление с раскладкой шагов по статусам транша и числом
 * уровней; действия — с типами условия, блоками настроек и ключами
 * строк.
 */
class StrategyStepAndActionTest {

    @ParameterizedTest
    @EnumSource(value = StrategyStepType.class, names = {"ENTRY", "GRID_ENTRY"})
    @DisplayName("U16.1 — тип шага — обычный вход либо вход уровнем сетки")
    void u16_1_entryStepTypes(StrategyStepType stepType) {
        assertThat(step(stepType).isEntryStep()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StrategyStepType.class, names = {"ENTRY", "GRID_ENTRY"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U16.2 — любой иной тип шага")
    void u16_2_nonEntryStepTypes(StrategyStepType stepType) {
        assertThat(step(stepType).isEntryStep()).isFalse();
    }

    @Test
    @DisplayName("U16.3 — шаг с действиями трёх классов")
    void u16_3_theFirstOrderActionIsPicked() {
        StrategyOrderAction orderAction = orderAction(2L, StrategyTradeDirection.LONG, false);
        StrategyStep subject = stepWith(StrategyStepType.ENTRY,
                positionAction(1L), orderAction, protectiveCreate(3L, AlgoOrder.ConditionType.STOP_LOSS));

        assertThat(subject.firstOrderAction()).containsSame(orderAction);
    }

    /** Пусто, а не отказ. */
    @Test
    @DisplayName("U16.4 — шаг без действий-заявок")
    void u16_4_noOrderActionGivesEmptiness() {
        assertThat(stepWith(StrategyStepType.EXIT, positionAction(1L)).firstOrderAction()).isEmpty();
        assertThat(step(StrategyStepType.EXIT).firstOrderAction()).isEmpty();
    }

    /** Замещение адресует уже стоящую ступень. */
    @Test
    @DisplayName("U16.5 — защитные создающие действия и одно замещающее")
    void u16_5_theLadderHoldsOnlyCreatingActions() {
        StrategyAlgoOrderAction created = protectiveCreate(1L, AlgoOrder.ConditionType.STOP_LOSS);
        StrategyAlgoOrderAction replacing = protectiveCreate(2L, AlgoOrder.ConditionType.STOP_LOSS);
        replacing.setActionType(StrategyActionType.REPLACE_ACTION);
        StrategyStep subject = stepWith(StrategyStepType.MAIN_PROTECTION, created, replacing);

        assertThat(subject.protectionLadderSteps()).containsExactly(created);
    }

    /** Защитой считается то, что несёт уровень остановки убытка. */
    @Test
    @DisplayName("U16.6 — создание тейк-профита рядом с созданием остановки убытка")
    void u16_6_takeProfitStaysOutOfTheLadder() {
        StrategyAlgoOrderAction stop = protectiveCreate(1L, AlgoOrder.ConditionType.STOP_LOSS);
        StrategyStep subject = stepWith(StrategyStepType.MAIN_PROTECTION,
                stop, protectiveCreate(2L, AlgoOrder.ConditionType.TAKE_PROFIT));

        assertThat(subject.protectionLadderSteps()).containsExactly(stop);
    }

    /** Последняя ступень получает остаток после округления предыдущих вниз. */
    @Test
    @DisplayName("U16.7 — лестница из трёх ступеней с разными ключами строк")
    void u16_7_theLastLadderStepIsTheHighestKey() {
        StrategyAlgoOrderAction last = protectiveCreate(3L, AlgoOrder.ConditionType.STOP_LOSS);
        StrategyStep subject = stepWith(StrategyStepType.MAIN_PROTECTION,
                protectiveCreate(1L, AlgoOrder.ConditionType.STOP_LOSS),
                last,
                protectiveCreate(2L, AlgoOrder.ConditionType.STOP_LOSS));

        assertThat(subject.lastProtectionLadderStep()).isSameAs(last);
    }

    @Test
    @DisplayName("U16.8 — у одной ступени ключа строки нет")
    void u16_8_aKeylessLadderStepIsNotTheLast() {
        StrategyAlgoOrderAction keyed = protectiveCreate(1L, AlgoOrder.ConditionType.STOP_LOSS);
        StrategyStep subject = stepWith(StrategyStepType.MAIN_PROTECTION,
                protectiveCreate(null, AlgoOrder.ConditionType.STOP_LOSS), keyed);

        assertThat(subject.lastProtectionLadderStep()).isSameAs(keyed);
    }

    @Test
    @DisplayName("U16.9 — защитных создающих действий у шага нет")
    void u16_9_anEmptyLadderHasNoLastStep() {
        assertThat(stepWith(StrategyStepType.EXIT, positionAction(1L)).lastProtectionLadderStep()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AlgoOrder.ConditionType.class,
            names = {"STOP_LOSS", "PARTIAL_STOP_LOSS", "OCO_FULL", "TRAILING_PERCENTS", "TRAILING_VALUE"})
    @DisplayName("U16.10 — каждый из пяти защитных типов условия порознь")
    void u16_10_theProtectiveConditionTypes(AlgoOrder.ConditionType conditionType) {
        assertThat(protectiveCreate(1L, conditionType).isProtective()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AlgoOrder.ConditionType.class, names = {"TAKE_PROFIT", "PARTIAL_TAKE_PROFIT"})
    @DisplayName("U16.11 — тип условия — тейк-профит либо частичный тейк-профит")
    void u16_11_takeProfitIsNotProtective(AlgoOrder.ConditionType conditionType) {
        assertThat(protectiveCreate(1L, conditionType).isProtective()).isFalse();
    }

    @Test
    @DisplayName("U16.12 — защитное действие с объявленным блоком трейлинга")
    void u16_12_aTrailingBlockMakesTheLevelObserved() {
        StrategyAlgoOrderAction subject = protectiveCreate(1L, AlgoOrder.ConditionType.TRAILING_PERCENTS);
        subject.setTrailingSettings(new TrailingSettings(null, null, null));

        assertThat(subject.levelSource()).isEqualTo(StrategyLevelSource.OBSERVED);
    }

    @Test
    @DisplayName("U16.13 — защитное действие с объявленным блоком остановки убытка")
    void u16_13_aStopLossBlockKeepsTheLevelDeclared() {
        StrategyAlgoOrderAction subject = protectiveCreate(1L, AlgoOrder.ConditionType.STOP_LOSS);
        subject.setStopLossSettings(new StopLossSettings(null, null, null, null, null));

        assertThat(subject.levelSource()).isEqualTo(StrategyLevelSource.DECLARED);
    }

    /**
     * Продуктового ожидания здесь нет: конфигурация отвергается созданием
     * (docs/rules/strategy-validation.md). Кейс прогоняем как охрана
     * ВТОРОГО рубежа — реализация отдаёт наблюдаемый источник.
     */
    @Test
    @DisplayName("U16.14 — защитное действие с обоими блоками")
    void u16_14_bothBlocksGiveTheObservedSource() {
        StrategyAlgoOrderAction subject = protectiveCreate(1L, AlgoOrder.ConditionType.TRAILING_PERCENTS);
        subject.setStopLossSettings(new StopLossSettings(null, null, null, null, null));
        subject.setTrailingSettings(new TrailingSettings(null, null, null));

        assertThat(subject.levelSource()).isEqualTo(StrategyLevelSource.OBSERVED);
    }

    /** Уровень задаёт только встроенная защита. */
    @Test
    @DisplayName("U16.15 — действие-заявка любой конфигурации")
    void u16_15_anOrderActionAlwaysDeclaresItsLevel() {
        assertThat(orderAction(1L, StrategyTradeDirection.LONG, false).levelSource())
                .isEqualTo(StrategyLevelSource.DECLARED);
        assertThat(orderAction(2L, StrategyTradeDirection.SHORT, true).levelSource())
                .isEqualTo(StrategyLevelSource.DECLARED);
    }

    /** Выход уровня не ставит и не переносит. */
    @Test
    @DisplayName("U16.16 — действие над позицией")
    void u16_16_aPositionActionNeverCarriesATarget() {
        StrategyPositionAction subject = positionAction(1L);

        assertThat(subject.levelSource()).isEqualTo(StrategyLevelSource.DECLARED);
        assertThat(subject.getTargetActionKey()).isNull();
    }

    @Test
    @DisplayName("U16.17 — направление длинное, действие не помечено уменьшающим")
    void u16_17_longEntryBuys() {
        assertThat(orderAction(1L, StrategyTradeDirection.LONG, false).side()).isEqualTo(Order.Side.BUY);
    }

    @Test
    @DisplayName("U16.18 — направление длинное, действие помечено уменьшающим")
    void u16_18_longExitSells() {
        assertThat(orderAction(1L, StrategyTradeDirection.LONG, true).side()).isEqualTo(Order.Side.SELL);
    }

    @Test
    @DisplayName("U16.19 — направление короткое, действие не помечено уменьшающим")
    void u16_19_shortEntrySells() {
        assertThat(orderAction(1L, StrategyTradeDirection.SHORT, false).side()).isEqualTo(Order.Side.SELL);
    }

    @Test
    @DisplayName("U16.20 — направление короткое, действие помечено уменьшающим")
    void u16_20_shortExitBuys() {
        assertThat(orderAction(1L, StrategyTradeDirection.SHORT, true).side()).isEqualTo(Order.Side.BUY);
    }

    /** Пустая пометка читается как «не уменьшает». */
    @Test
    @DisplayName("U16.21 — пометка «уменьшает позицию» пуста")
    void u16_21_anAbsentReducingFlagReadsAsNotReducing() {
        assertThat(orderAction(1L, StrategyTradeDirection.LONG, null).side()).isEqualTo(Order.Side.BUY);
        assertThat(orderAction(2L, StrategyTradeDirection.SHORT, null).side()).isEqualTo(Order.Side.SELL);
    }

    @Test
    @DisplayName("U16.22 — объявление с числом уровней больше единицы")
    void u16_22_theDeclaredLevelCountMaterialises() {
        assertThat(declarationWithLevels(3).materializedCount()).isEqualTo(3);
    }

    /** Охрана второго рубежа: пустое до рантайма не доезжает. */
    @Test
    @DisplayName("U16.23 — число уровней пусто либо меньше единицы")
    void u16_23_anAbsentOrNonPositiveLevelCountMaterialisesOne() {
        assertThat(declarationWithLevels(null).materializedCount()).isEqualTo(1);
        assertThat(declarationWithLevels(0).materializedCount()).isEqualTo(1);
        assertThat(declarationWithLevels(-2).materializedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("U16.24 — объявление, чьи шаги в статусе кандидата несут входные")
    void u16_24_aPrecheckEntryStepMakesItAnEntryDeclaration() {
        StrategyStep entry = step(StrategyStepType.ENTRY);
        StrategyTranche subject = declaration(Map.of(DealTranche.Status.PRECHECK, List.of(entry)));

        assertThat(subject.isEntryDeclaration()).isTrue();
        assertThat(subject.entrySteps()).containsExactly(entry);
    }

    @Test
    @DisplayName("U16.25 — объявление без шагов в статусе кандидата")
    void u16_25_noPrecheckStepsNoEntryDeclaration() {
        StrategyTranche subject = declaration(Map.of(DealTranche.Status.MANAGING,
                List.of(step(StrategyStepType.ENTRY))));

        assertThat(subject.isEntryDeclaration()).isFalse();
        assertThat(subject.entrySteps()).isEmpty();
        assertThat(declaration(null).entrySteps()).isEmpty();
    }

    @Test
    @DisplayName("U16.26 — шаги в статусе кандидата входными не являются")
    void u16_26_nonEntryPrecheckStepsDoNotDeclareAnEntry() {
        StrategyTranche subject = declaration(Map.of(DealTranche.Status.PRECHECK,
                List.of(step(StrategyStepType.FAIL_SAFE))));

        assertThat(subject.isEntryDeclaration()).isFalse();
    }

    @Test
    @DisplayName("U16.27 — шаги сделочного уровня по статусу сделки")
    void u16_27_dealLevelStepsAreTakenByStatus() {
        StrategyStep ofActive = step(StrategyStepType.EXIT);
        StrategyDetail subject = new StrategyDetail();
        subject.setStepsByStatus(Map.of(Deal.Status.ACTIVE, List.of(ofActive)));

        assertThat(subject.dealLevelSteps(Deal.Status.ACTIVE)).containsExactly(ofActive);
        assertThat(subject.dealLevelSteps(Deal.Status.EXIT_PENDING)).isEmpty();
        assertThat(new StrategyDetail().dealLevelSteps(Deal.Status.ACTIVE)).isEmpty();
    }

    /**
     * Роль размещения: замещение с непустым ключом цели даёт ПЕРЕНОС
     * (пробел `G2` документа, добран под-шагом 3).
     */
    @Test
    @DisplayName("U16.28 — замещение с непустым ключом цели")
    void u16_28_aReplacementWithATargetIsATransfer() {
        StrategyAlgoOrderAction subject = protectiveCreate(1L, AlgoOrder.ConditionType.STOP_LOSS);
        subject.setActionType(StrategyActionType.REPLACE_ACTION);
        subject.setTargetActionKey("prot-1");

        assertThat(subject.placementRole()).isEqualTo(StrategyPlacementRole.TRANSFER);
    }

    /** Всё прочее — первичная постановка (пробел `G2`). */
    @Test
    @DisplayName("U16.29 — замещение без ключа цели и создание с ключом цели")
    void u16_29_everythingElseIsAPrimaryPlacement() {
        StrategyAlgoOrderAction blankTarget = protectiveCreate(1L, AlgoOrder.ConditionType.STOP_LOSS);
        blankTarget.setActionType(StrategyActionType.REPLACE_ACTION);
        blankTarget.setTargetActionKey("  ");
        StrategyAlgoOrderAction creating = protectiveCreate(2L, AlgoOrder.ConditionType.STOP_LOSS);
        creating.setTargetActionKey("prot-1");

        assertThat(blankTarget.placementRole()).isEqualTo(StrategyPlacementRole.PRIMARY);
        assertThat(creating.placementRole()).isEqualTo(StrategyPlacementRole.PRIMARY);
        assertThat(positionAction(3L).placementRole()).isEqualTo(StrategyPlacementRole.PRIMARY);
    }

    private static StrategyStep step(StrategyStepType stepType) {
        StrategyStep step = new StrategyStep();
        step.setStepType(stepType);
        return step;
    }

    private static StrategyStep stepWith(StrategyStepType stepType, StrategyAction... actions) {
        StrategyStep step = step(stepType);
        step.setActions(List.of(actions));
        return step;
    }

    private static StrategyAlgoOrderAction protectiveCreate(Long id, AlgoOrder.ConditionType conditionType) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(id);
        action.setKey("algo-" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(conditionType);
        return action;
    }

    private static StrategyOrderAction orderAction(Long id, StrategyTradeDirection direction,
                                                   Boolean positionReducingOnly) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(id);
        action.setKey("order-" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setDirection(direction);
        action.setPositionReducingOnly(positionReducingOnly);
        return action;
    }

    private static StrategyPositionAction positionAction(Long id) {
        StrategyPositionAction action = new StrategyPositionAction();
        action.setId(id);
        action.setKey("position-" + id);
        action.setActionType(StrategyActionType.EXIT_ACTION);
        return action;
    }

    private static StrategyTranche declarationWithLevels(Integer levelCount) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setLevelCount(levelCount);
        return declaration;
    }

    private static StrategyTranche declaration(Map<DealTranche.Status, List<StrategyStep>> stepsByStatus) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setStepsByStatus(stepsByStatus);
        return declaration;
    }
}
