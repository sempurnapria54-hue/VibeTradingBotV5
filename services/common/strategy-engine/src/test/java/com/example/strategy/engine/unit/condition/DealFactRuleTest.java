package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.livePosition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.order;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static com.example.strategy.engine.unit.condition.ConditionFixture.tranche;
import static com.example.strategy.engine.unit.condition.ConditionFixture.trancheWithAttachedProtection;
import static com.example.strategy.engine.unit.condition.ConditionFixture.trancheWithOrders;
import static com.example.strategy.engine.unit.condition.ConditionFixture.trancheWithStandaloneProtection;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Факты сделки: эпизод, вход, защиты — группа `U9` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (docs/spec/deal-condition.json; модели —
 * docs/models/domain/core/Position.md, docs/models/domain/core/Order.md,
 * docs/models/domain/aggregate/DealTranche.md).
 *
 * <p><b>Базовая сборка:</b> живой эпизод — статус `ACTIVE`, наблюдённый
 * размер {@code 1}, средняя цена входа {@code 100}; транш без заявок и без
 * отдельных защит; правило `POSITION_OPENED`.
 *
 * <p><b>Состояние собирается настоящими полями, а не подменёнными
 * предикатами:</b> живой риск эпизода вытекает из статуса и наблюдённого
 * размера, налив входной ноги — из статуса и причины закрытия, встроенная
 * защита — из состава заявок транша (.claude/rules/codestyle.md §«Тесты
 * доменных моделей»).
 */
class DealFactRuleTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Живой эпизод есть — позиция открыта. */
    @Test
    @DisplayName("U9.1 — базовая сборка: истина")
    void u9_1_theBaseAssemblyIsTrue() {
        assertThat(evaluate(StrategyConditionRuleType.POSITION_OPENED, livePosition("100"), tranche())).isTrue();
    }

    /** Строка эпизода без положительного размера — факт наблюдения, а не экспозиция. */
    @Test
    @DisplayName("U9.2 — эпизод ACTIVE, наблюдённый размер 0: ложь")
    void u9_2_anObservedEpisodeWithoutSizeIsNotAnOpenPosition() {
        assertThat(evaluate(StrategyConditionRuleType.POSITION_OPENED, sizedPosition("0"), tranche())).isFalse();
    }

    /** Вторая половина предиката живого риска. */
    @Test
    @DisplayName("U9.3 — эпизод CLOSED, наблюдённый размер 1: ложь")
    void u9_3_aClosedEpisodeCarriesNoLiveRisk() {
        Position closed = livePosition("100");
        closed.setStatus(Position.Status.CLOSED);

        assertThat(evaluate(StrategyConditionRuleType.POSITION_OPENED, closed, tranche())).isFalse();
    }

    /** Недоступный операнд консервативно ложен. */
    @Test
    @DisplayName("U9.4 — эпизода в контексте нет: ложь")
    void u9_4_anAbsentEpisodeIsFalse() {
        assertThat(evaluate(StrategyConditionRuleType.POSITION_OPENED, null, tranche())).isFalse();
    }

    /** Строгое отрицание U9.1. */
    @Test
    @DisplayName("U9.5 — NO_OPEN_POSITION на базовой сборке: ложь — строгое отрицание U9.1")
    void u9_5_theAbsenceRuleIsTheStrictNegation() {
        assertThat(evaluate(StrategyConditionRuleType.NO_OPEN_POSITION, livePosition("100"), tranche())).isFalse();
    }

    /** Эпизода нет — отрицание истинно. */
    @Test
    @DisplayName("U9.6 — NO_OPEN_POSITION, эпизода нет: истина")
    void u9_6_theAbsenceRuleIsTrueWithoutAnEpisode() {
        assertThat(evaluate(StrategyConditionRuleType.NO_OPEN_POSITION, null, tranche())).isTrue();
    }

    /** Обе величины читают ОДИН предикат, и расхождения между ними не бывает по построению. */
    @Test
    @DisplayName("U9.7 — NO_OPEN_POSITION, эпизод ACTIVE с размером 0: истина")
    void u9_7_bothRulesReadTheSamePredicate() {
        assertThat(evaluate(StrategyConditionRuleType.NO_OPEN_POSITION, sizedPosition("0"), tranche())).isTrue();
    }

    /** Нога налита целиком. */
    @Test
    @DisplayName("U9.8 — ENTRY_ORDER_FINALIZED, входная нога COMPLETED/FILLED: истина")
    void u9_8_aFullyFilledEntryLegFinalizesTheEntry() {
        DealTranche filled = trancheWithOrders(
                order(800L, Order.Status.COMPLETED, Order.CloseReason.FILLED, false));

        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"), filled)).isTrue();
    }

    /** Частичный налив финализацией не является — экспозиция ещё меняется. */
    @Test
    @DisplayName("U9.9 — та же нога PARTIALLY_COMPLETED: ложь")
    void u9_9_aPartialFillIsNotAFinalization() {
        DealTranche partial = trancheWithOrders(
                order(800L, Order.Status.PARTIALLY_COMPLETED, null, false));

        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"), partial)).isFalse();
    }

    /** Завершённость без налива финализацией не является. */
    @Test
    @DisplayName("U9.10 — нога COMPLETED с причиной CANCELED_BY_STRATEGY: ложь")
    void u9_10_aCompletedButCancelledLegIsNotFilled() {
        DealTranche cancelled = trancheWithOrders(
                order(800L, Order.Status.COMPLETED, Order.CloseReason.CANCELED_BY_STRATEGY, false));

        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"), cancelled))
                .isFalse();
    }

    /** Признак входа берётся у НАМЕРЕНИЯ заявки, а не у её статуса. */
    @Test
    @DisplayName("U9.11 — у транша только reduce-only заявка COMPLETED/FILLED: ложь")
    void u9_11_aReduceOnlyLegIsNotAnEntryLeg() {
        DealTranche reduceOnly = trancheWithOrders(
                order(800L, Order.Status.COMPLETED, Order.CloseReason.FILLED, true));

        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"), reduceOnly))
                .isFalse();
    }

    /** Входная нога — ПОСЛЕДНЯЯ: замещение входа заводит новую заявку. */
    @Test
    @DisplayName("U9.12 — две входные ноги, налита та, у которой идентификатор больше: истина")
    void u9_12_theEntryLegIsTheLatestOne() {
        DealTranche replaced = trancheWithOrders(
                order(800L, Order.Status.PARTIALLY_COMPLETED, null, false),
                order(900L, Order.Status.COMPLETED, Order.CloseReason.FILLED, false));

        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"), replaced)).isTrue();
    }

    /** Транша нет — недоступный операнд. */
    @Test
    @DisplayName("U9.13 — транша в контексте нет: ложь")
    void u9_13_anAbsentTrancheIsFalse() {
        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"), null)).isFalse();
    }

    /** Встроенная защита жива — второй предикат при этом ложен. */
    @Test
    @DisplayName("U9.14 — живая встроенная защита: ATTACHED — истина, MAIN — ложь")
    void u9_14_theAttachedProtectionIsDistinguishedFromTheStandaloneOne() {
        DealTranche attached = trancheWithAttachedProtection(AttachedAlgoOrder.Status.ACTIVE);

        assertThat(evaluate(StrategyConditionRuleType.ATTACHED_STOP_LOSS_EXISTS, livePosition("100"), attached))
                .isTrue();
        assertThat(evaluate(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS, livePosition("100"), attached))
                .isFalse();
    }

    /** Пара к U9.14: на этом различении стои́т сценарий переключения защиты. */
    @Test
    @DisplayName("U9.15 — живая отдельная защита со стоп-уровнем: ATTACHED — ложь, MAIN — истина")
    void u9_15_theStandaloneProtectionIsDistinguishedFromTheAttachedOne() {
        DealTranche standalone = trancheWithStandaloneProtection(AlgoOrder.ConditionType.STOP_LOSS);

        assertThat(evaluate(StrategyConditionRuleType.ATTACHED_STOP_LOSS_EXISTS, livePosition("100"), standalone))
                .isFalse();
        assertThat(evaluate(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS, livePosition("100"), standalone))
                .isTrue();
    }

    /** Предикат спрашивает ЖИВУЮ защиту. */
    @Test
    @DisplayName("U9.16 — встроенная защита есть, но её статус терминальный: ATTACHED — ложь")
    void u9_16_aTerminalAttachedProtectionIsNotAlive() {
        DealTranche cancelled = trancheWithAttachedProtection(AttachedAlgoOrder.Status.CANCELED);

        assertThat(evaluate(StrategyConditionRuleType.ATTACHED_STOP_LOSS_EXISTS, livePosition("100"), cancelled))
                .isFalse();
    }

    /** Вопрос ровно в том, встала ли ОСНОВНАЯ — тейк уровня остановки убытка не несёт. */
    @Test
    @DisplayName("U9.17 — отдельная защита жива, но действующего стоп-уровня не несёт (тейк): MAIN — ложь")
    void u9_17_aTakeProfitCarriesNoStopLevel() {
        DealTranche take = trancheWithStandaloneProtection(AlgoOrder.ConditionType.TAKE_PROFIT);

        assertThat(evaluate(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS, livePosition("100"), take)).isFalse();
    }

    /** Оба предиката защиты на пустом траншé ложны. */
    @Test
    @DisplayName("U9.18 — оба типа правил, транша в контексте нет: оба ложны")
    void u9_18_bothProtectionRulesAreFalseWithoutATranche() {
        assertThat(evaluate(StrategyConditionRuleType.ATTACHED_STOP_LOSS_EXISTS, livePosition("100"), null))
                .isFalse();
        assertThat(evaluate(StrategyConditionRuleType.MAIN_PROTECTION_EXISTS, livePosition("100"), null))
                .isFalse();
    }

    /** Финализирует терминал ноги, а не полнота налива: у снятой налив окончателен. */
    @Test
    @DisplayName("U9.19 — ENTRY_ORDER_FINALIZED, входная нога снята после частичного налива: истина")
    void u9_19_aLegCancelledAfterAPartialFillFinalizesTheEntry() {
        Order cancelled = order(800L, Order.Status.CANCELED, Order.CloseReason.CANCELED_BY_STRATEGY, false);
        cancelled.setAccumulatedFillSize(new BigDecimal("2"));

        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"),
                trancheWithOrders(cancelled))).isTrue();
    }

    /** Недобытый налив нулём не подменяется и финализацией не читается. */
    @Test
    @DisplayName("U9.20 — та же нога, налив не добыт: ложь")
    void u9_20_aCancelledLegWithAnUnknownFillIsNotAFinalization() {
        Order cancelled = order(800L, Order.Status.CANCELED, Order.CloseReason.CANCELED_BY_STRATEGY, false);
        cancelled.setAccumulatedFillSize(null);

        assertThat(evaluate(StrategyConditionRuleType.ENTRY_ORDER_FINALIZED, livePosition("100"),
                trancheWithOrders(cancelled))).isFalse();
    }

    private Boolean evaluate(StrategyConditionRuleType type, Position position, DealTranche dealTranche) {
        ConditionEvaluationContext context = base()
                .activePosition(position)
                .tranche(dealTranche)
                .build();
        return evaluator.evaluate(condition(rule(type)), context);
    }

    private Position sizedPosition(String size) {
        Position position = livePosition("100");
        position.setExternalSize(new BigDecimal(size));
        return position;
    }
}
