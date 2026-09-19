package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.STRUCTURE_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.livePosition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.number;
import static com.example.strategy.engine.unit.condition.ConditionFixture.operandOfSource;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static com.example.strategy.engine.unit.condition.ConditionFixture.structure;
import static com.example.strategy.engine.unit.condition.ConditionFixture.structureOperand;
import static com.example.strategy.engine.unit.condition.ConditionFixture.trancheWithStandaloneProtection;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Типы без исполнения: журнальная ветвь и константная истина — группа
 * `U12` документа `.claude/tests/cases/strategy-engine-condition.md`
 * (docs/rules/strategy-condition-contract.md §«Источник истины — объектная
 * модель настроек», перечень типов без дома).
 *
 * <p><b>Базовая сборка:</b> контекст сделки со всеми фактами; захват
 * вывода логгера включён.
 *
 * <p><b>Предмет группы — РАЗЛИЧЕНИЕ трёх причин лжи.</b> По значению они
 * неразличимы: предикат посчитан и ложен; операнд недоступен; тип либо
 * оператор интерпретатором не исполняется вовсе. Третью наблюдает
 * единственный след — запись журнала, и потому строки называют лог
 * предметом ожидания наравне с булевым значением.
 */
class UnevaluableRuleTypeTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /**
     * Операнд типа — реестр сделок пары, и приносит его отбор входа, а не
     * проход сделки: молчаливой ложью тип не оборачивается.
     */
    @Test
    @DisplayName("U12.1 — правило NO_ACTIVE_DEAL: ложь И запись журнала, называющая тип")
    void u12_1_anUnevaluableTypeNamesItselfInTheLog() {
        try (EvaluatorLogCapture log = EvaluatorLogCapture.attach()) {
            assertThat(evaluate(rule(StrategyConditionRuleType.NO_ACTIVE_DEAL))).isFalse();

            assertThat(log.messages())
                    .as("единственный след, кроме ответа, — предупреждение о неисполнимом типе")
                    .hasSize(1);
            assertThat(log.messages().getFirst())
                    .as("запись называет ТИП: без имени она не адресует")
                    .contains(StrategyConditionRuleType.NO_ACTIVE_DEAL.name());
        }
    }

    /**
     * Пара к U12.1: два типа различаются одним словом, один исполняется,
     * другой нет, а значение у обоих одно — различает их только след.
     */
    @Test
    @DisplayName("U12.2 — правило NO_OPEN_POSITION на той же сборке: ложь БЕЗ записи журнала")
    void u12_2_anEvaluatedTypeIsSilentlyFalse() {
        try (EvaluatorLogCapture log = EvaluatorLogCapture.attach()) {
            assertThat(evaluate(rule(StrategyConditionRuleType.NO_OPEN_POSITION)))
                    .as("эпизод живой, строгое отрицание даёт ложь честно")
                    .isFalse();

            assertThat(log.messages()).as("исполняемый тип следа не оставляет").isEmpty();
        }
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Тип
     * объявлен защитой от look-ahead, и создание требует у него таймфрейм
     * (docs/models/domain/aggregate/Strategy.md §Условия). Код отвечает
     * истиной при любом входе — таймфрейма он не читает, а операнда
     * закрытия свечи в контексте нет вовсе: единственная защита от
     * заглядывания вперёд не мерит ничего, и эталонное определение на неё
     * опирается. Красный прогон и есть предъявление находки `F-5`
     * (`.claude/work/backlog.md` §«Тип `CANDLE_CLOSED` константно истинен,
     * а объявлен защитой от look-ahead»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U12.3 — CANDLE_CLOSED с таймфреймом, свеча не закрыта: ложь (дом), код даёт истину")
    void u12_3_theCandleClosedGuardMustReadTheCandle() {
        StrategyConditionRule candleRule = rule(StrategyConditionRuleType.CANDLE_CLOSED);
        candleRule.setTimeframe(TimeFrame.ONE_HOUR);

        assertThat(evaluate(candleRule))
                .as("защита от look-ahead обязана мерить закрытие объявленной свечи")
                .isFalse();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе</b> — та же
     * находка `F-5` со второй стороны: без таймфрейма мерить нечего тем
     * более. Охрана второго рубежа: создание таймфрейм требует
     * (docs/rules/strategy-validation.md).
     */
    @Test
    @Tag("debt")
    @DisplayName("U12.4 — CANDLE_CLOSED без таймфрейма: ложь (дом), код даёт истину")
    void u12_4_theCandleClosedGuardWithoutATimeframeIsFalse() {
        assertThat(evaluate(rule(StrategyConditionRuleType.CANDLE_CLOSED)))
                .as("без объявленной свечи защита мерить не может ничего")
                .isFalse();
    }

    /**
     * Три типа источника операнда, объявленных перечнем грамматики:
     * создание разрешает их в клаузе фазы, интерпретатор не резолвит и о
     * том НЕ сообщает — молчаливая ложь.
     *
     * <p>Поле момента оценки в контексте при этом не читает никто —
     * находка `F-6`. <b>Охрана второго рубежа:</b> сравнивающему правилу
     * создание требует операнд названного источника, и пара
     * «нерезолвимый источник плюс константа» его не несёт.
     */
    @ParameterizedTest(name = "тип источника {0}: ложь без записи журнала")
    @EnumSource(value = StrategyConditionSourceType.class, names = {"TIME", "BALANCE", "MARKET_PHASE"})
    @DisplayName("U12.5-U12.7 — три типа источника операнда: ложь БЕЗ записи журнала")
    void u12_5_to_u12_7_theUnresolvedSourceTypesAreSilentlyFalse(StrategyConditionSourceType sourceType) {
        assertUnresolvedSourceIsSilentlyFalse(sourceType);
    }

    /** Структура есть, второго операнда нет — ложь без записи журнала. */
    @Test
    @DisplayName("U12.8 — MARKET_STRUCTURE_IS, структурный операнд есть, второго нет: ложь без записи журнала")
    void u12_8_aStructureRuleWithoutItsConstantIsSilentlyFalse() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.MARKET_STRUCTURE_IS,
                StrategyConditionOperator.EQ, structureOperand(STRUCTURE_KEY), null));

        try (EvaluatorLogCapture log = EvaluatorLogCapture.attach()) {
            assertThat(evaluator.evaluate(condition, context())).isFalse();

            assertThat(log.messages()).isEmpty();
        }
    }

    /**
     * Остальные типы источника того же класса: одна ветвь
     * ({@code #resolveScalar}, {@code default}), шесть имён — три покрыты
     * строками `U12.5`-`U12.7`.
     *
     * <p>Строка добрана под-шагом 3 по пробелу `G2`.
     */
    @ParameterizedTest(name = "U12.9 — тип источника {0}: ложь без записи журнала")
    @EnumSource(value = StrategyConditionSourceType.class, names = {"POSITION", "ORDER", "ALGO_ORDER"})
    @DisplayName("U12.9 — три оставшихся типа источника без исполнения: ложь БЕЗ записи журнала")
    void u12_9_theRemainingUnresolvedSourceTypesAreSilentlyFalse(StrategyConditionSourceType sourceType) {
        assertUnresolvedSourceIsSilentlyFalse(sourceType);
    }

    private void assertUnresolvedSourceIsSilentlyFalse(StrategyConditionSourceType sourceType) {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE,
                StrategyConditionOperator.GT, operandOfSource(sourceType), number("10")));

        try (EvaluatorLogCapture log = EvaluatorLogCapture.attach()) {
            assertThat(evaluator.evaluate(condition, context()))
                    .as("тип источника %s объявлен перечнем и не резолвится", sourceType)
                    .isFalse();

            assertThat(log.messages())
                    .as("о нерезолвленном источнике интерпретатор не сообщает — молчаливая ложь")
                    .isEmpty();
        }
    }

    private Boolean evaluate(StrategyConditionRule singleRule) {
        return evaluator.evaluate(condition(singleRule), context());
    }

    /** Контекст сделки со ВСЕМИ фактами: молчание здесь не следствие пустоты операндов. */
    private ConditionEvaluationContext context() {
        Map<String, MarketStructure> structures = Map.of(STRUCTURE_KEY, structure(MarketStructure.Type.RANGE));
        return base()
                .structures(structures)
                .price(new BigDecimal("103"))
                .marketPhase(MarketPhase.Type.BULL_TREND)
                .entryMarketPhase(MarketPhase.Type.BULL_TREND)
                .direction(StrategyTradeDirection.LONG)
                .activePosition(livePosition("100"))
                .tranche(trancheWithStandaloneProtection(AlgoOrder.ConditionType.STOP_LOSS))
                .build();
    }
}
