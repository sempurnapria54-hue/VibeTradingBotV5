package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.constant;
import static com.example.strategy.engine.unit.condition.ConditionFixture.livePosition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Пороги хода: знак, направление, граница — группа `U10` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (docs/spec/deal-condition.json, величины {@code signedMovePercents},
 * {@code profitPercentsReached}, {@code lossPercentsReached}).
 *
 * <p><b>Базовая сборка:</b> живой эпизод — `ACTIVE`, размер {@code 1},
 * средняя цена входа {@code 100}; цена момента {@code 103}; направление
 * `LONG`; правило `PROFIT_PERCENTS_REACHED`, порог {@code 2} объявлен
 * <b>обеими формами сразу</b> — плоским полем {@code percents} (её требует
 * создание) и константным операндом (её читает интерпретатор).
 *
 * <p><b>Форма базовой сборки выбрана так не для удобства:</b> правило БЕЗ
 * плоского поля создание отвергает, а правило БЕЗ константного операнда
 * интерпретатор читает ложью. Обе половины по отдельности предъявляют
 * строки `U10.13` и `U10.14`.
 */
class MoveThresholdTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Ход `+3` процента взял порог `2`. */
    @Test
    @DisplayName("U10.1 — базовая сборка: ход +3 % взял порог 2 — истина")
    void u10_1_theBaseAssemblyReachesTheProfitThreshold() {
        assertThat(profit("100", "103", StrategyTradeDirection.LONG)).isTrue();
    }

    /** Пара к U10.1. */
    @Test
    @DisplayName("U10.2 — цена момента 101: ложь")
    void u10_2_aSmallerMoveDoesNotReachTheThreshold() {
        assertThat(profit("100", "101", StrategyTradeDirection.LONG)).isFalse();
    }

    /** Граница ВКЛЮЧАЮЩАЯ: строгое сравнение сдвинуло бы срабатывание на тик. */
    @Test
    @DisplayName("U10.3 — цена момента 102: истина — граница включающая")
    void u10_3_theThresholdBoundaryIsInclusive() {
        assertThat(profit("100", "102", StrategyTradeDirection.LONG)).isTrue();
    }

    /** Отрицательный ход порога прибыли не берёт. */
    @Test
    @DisplayName("U10.4 — цена момента 97: ложь — ход отрицателен")
    void u10_4_aNegativeMoveIsNotAProfit() {
        assertThat(profit("100", "97", StrategyTradeDirection.LONG)).isFalse();
    }

    /** Тот же ход вниз у короткой стороны есть ПРИБЫЛЬ — пара к U10.4. */
    @Test
    @DisplayName("U10.5 — направление SHORT, цена момента 97: истина")
    void u10_5_theSameMoveIsAProfitForTheShortSide() {
        assertThat(profit("100", "97", StrategyTradeDirection.SHORT)).isTrue();
    }

    /** Порог убытка объявлен ПОЛОЖИТЕЛЬНОЙ величиной и сравнивается с отрицанием хода. */
    @Test
    @DisplayName("U10.6 — LOSS_PERCENTS_REACHED, цена момента 97, порог 2: истина")
    void u10_6_theLossThresholdComparesAgainstTheNegatedMove() {
        assertThat(loss("100", "97", StrategyTradeDirection.LONG)).isTrue();
    }

    /** Пара к U10.6: без отрицания порога правило сработало бы в прибыли. */
    @Test
    @DisplayName("U10.7 — тот же тип, цена момента 103: ложь")
    void u10_7_theLossThresholdDoesNotFireInProfit() {
        assertThat(loss("100", "103", StrategyTradeDirection.LONG)).isFalse();
    }

    /** Та же включающая граница с другой стороны. */
    @Test
    @DisplayName("U10.8 — тот же тип, цена момента 98: истина — граница включающая")
    void u10_8_theLossBoundaryIsInclusiveToo() {
        assertThat(loss("100", "98", StrategyTradeDirection.LONG)).isTrue();
    }

    /** Вычисление на пустом операнде объявило бы полный убыток. */
    @Test
    @DisplayName("U10.9 — цены момента нет: ложь, а не срабатывание")
    void u10_9_anAbsentPriceDoesNotFireTheThreshold() {
        assertThat(profit("100", null, StrategyTradeDirection.LONG)).isFalse();
    }

    /** Плановой ценой якорь НЕ подменяется. */
    @Test
    @DisplayName("U10.10 — средней цены входа у эпизода нет: ложь")
    void u10_10_anAbsentEntryAnchorDoesNotFireTheThreshold() {
        assertThat(profit(null, "103", StrategyTradeDirection.LONG)).isFalse();
    }

    /** Эпизода нет вовсе — операндов хода нет. */
    @Test
    @DisplayName("U10.11 — эпизода нет вовсе: ложь")
    void u10_11_anAbsentEpisodeDoesNotFireTheThreshold() {
        StrategyCondition condition = moveRule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED, "2", "2");
        ConditionEvaluationContext context = base()
                .price(new BigDecimal("103"))
                .direction(StrategyTradeDirection.LONG)
                .build();

        assertThat(evaluator.evaluate(condition, context)).isFalse();
    }

    /** Нулевой якорь — ложь, а не деление на ноль. */
    @Test
    @DisplayName("U10.12 — средняя цена входа 0: ложь, а не деление на ноль")
    void u10_12_aZeroAnchorIsFalseAndNotADivisionByZero() {
        assertThat(profit("0", "103", StrategyTradeDirection.LONG)).isFalse();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Контракт
     * авторинга кладёт порог в ПЛОСКОЕ ПОЛЕ правила
     * (docs/rules/strategy-condition-contract.md §«Правило и операнды»),
     * создание его там и требует, и эталонное определение объявляет его
     * так. Код читает только константный операнд и отвечает ложью: оба
     * порога ложны всегда, и шаг перевода защиты в безубыток не
     * срабатывает ни разу. Красный прогон и есть предъявление находки
     * `F-1` (`.claude/work/backlog.md` §«Порог хода читается из операнда,
     * а объявляется плоским полем правила»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U10.13 — порог объявлен только плоским полем percents = 2: истина (дом), код даёт ложь")
    void u10_13_theThresholdDeclaredByTheFlatFieldIsRead() {
        StrategyCondition condition = moveRule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED, "2", null);

        assertThat(evaluator.evaluate(condition, moveContext("100", "103", StrategyTradeDirection.LONG)))
                .as("форма контракта авторинга — плоское поле; ход +3 %% взял порог 2")
                .isTrue();
    }

    /** Порог, прочитанный нулём, срабатывал бы на любом положительном ходе. */
    @Test
    @DisplayName("U10.14 — порога нет ни в одной форме: ложь")
    void u10_14_anAbsentThresholdDoesNotFire() {
        StrategyCondition condition = moveRule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED, null, null);

        assertThat(evaluator.evaluate(condition, moveContext("100", "103", StrategyTradeDirection.LONG))).isFalse();
    }

    /**
     * Пустое направление читается КОРОТКОЙ стороной, и порог прибыли
     * ложен. Охрана второго рубежа: перечень направлений сделки закрыт
     * двумя значениями, и пустого среди них нет.
     */
    @Test
    @DisplayName("U10.15 — направления в контексте нет, цена 103: ход читается как −3 % — ложь")
    void u10_15_anAbsentDirectionIsReadAsTheShortSide() {
        assertThat(profit("100", "103", null))
                .as("ветвь направления выбирается равенством LONG, и пустое в неё не попадает")
                .isFalse();
        assertThat(loss("100", "103", null))
                .as("и тот же ход у порога убытка срабатывает — знак прочитан коротким")
                .isTrue();
    }

    /** Деление идёт в доменном десятичном контексте, а не в целых. */
    @Test
    @DisplayName("U10.16 — средняя цена входа 3, цена момента 10: ход 233.33… %, а не 200 %")
    void u10_16_theMoveIsComputedInTheDomainDecimalContext() {
        assertThat(evaluator.evaluate(
                moveRule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED, "233", "233"),
                moveContext("3", "10", StrategyTradeDirection.LONG)))
                .as("целочисленное деление дало бы 200 %% и порог 233 не взяло бы")
                .isTrue();
        assertThat(evaluator.evaluate(
                moveRule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED, "234", "234"),
                moveContext("3", "10", StrategyTradeDirection.LONG)))
                .as("и порог 234 тем же ходом не берётся")
                .isFalse();
    }

    private Boolean profit(String anchor, String price, StrategyTradeDirection direction) {
        return evaluator.evaluate(moveRule(StrategyConditionRuleType.PROFIT_PERCENTS_REACHED, "2", "2"),
                moveContext(anchor, price, direction));
    }

    private Boolean loss(String anchor, String price, StrategyTradeDirection direction) {
        return evaluator.evaluate(moveRule(StrategyConditionRuleType.LOSS_PERCENTS_REACHED, "2", "2"),
                moveContext(anchor, price, direction));
    }

    /**
     * Правило хода: порог объявляется плоским полем, константным операндом
     * либо обоими — по строке кейса.
     */
    private StrategyCondition moveRule(StrategyConditionRuleType type, String flatPercents, String operandPercents) {
        StrategyConditionRule moveRule = rule(type, StrategyConditionOperator.GTE, null,
                isNull(operandPercents) ? null : constant(operandPercents, ConstantValueType.PERCENT));
        if (nonNull(flatPercents)) {
            moveRule.setPercents(new BigDecimal(flatPercents));
        }
        return condition(moveRule);
    }

    private ConditionEvaluationContext moveContext(String anchor, String price, StrategyTradeDirection direction) {
        Position position = livePosition(anchor);
        return base()
                .activePosition(position)
                .direction(direction)
                .price(isNull(price) ? null : new BigDecimal(price))
                .build();
    }
}
