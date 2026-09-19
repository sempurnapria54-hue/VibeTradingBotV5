package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.absentOperandClause;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.phaseClause;
import static com.example.marketdata.unit.calculation.CalcFixture.unconditionalClause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.phase.MarketPhaseResolver;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Фаза — порядок клауз и консервативный дефолт: группа `U16` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/components/MarketPhaseResolver.md §«First-match по позиции»,
 * §Stateless, §Границы; docs/models/domain/other/MarketPhase.md §«Енум
 * `Type`», §«Вычисление и свежесть (на лету)»).
 *
 * <p><b>Базовая сборка:</b> упорядоченный список клауз «условие → фаза»;
 * контекст оценки с готовыми значениями; вычислитель условий —
 * <b>настоящий</b>, а не подменённый: ввода-вывода у него нет, и признак
 * мокания на него не распространяется
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 *
 * <p><b>Истинность условия обеспечена простейшим сравнением констант.</b>
 * Грамматику условий мерит соседний предмет
 * (`.claude/tests/cases/strategy-engine-condition.md`); здесь она средство,
 * и цена этого названа.
 *
 * <p>Клетка `U16.8` (у истинной клаузы тип фазы не проставлен) кода не
 * получила: дом клаузы без типа не называет.
 */
class PhaseClauseOrderTest {

    private final MarketPhaseResolver resolver = new MarketPhaseResolver(new StrategyConditionEvaluator());

    /** Единственная истинная клауза задаёт тип. */
    @Test
    @DisplayName("U16.1 — единственная клауза, условие истинно: тип её собственный, RANGE")
    void u16_1_theOnlySatisfiedClauseSetsTheType() {
        assertThat(resolve(List.of(phaseClause(MarketPhase.Type.RANGE, true)))).isEqualTo(MarketPhase.Type.RANGE);
    }

    /** Выбирает позиция в списке, а не «сила» клаузы и не порядок проверки условий. */
    @Test
    @DisplayName("U16.2 — истинны вторая и четвёртая клаузы: тип второй, RANGE")
    void u16_2_thePositionInTheListDecidesAndNotTheStrengthOfTheClause() {
        List<StrategyMarketPhaseRule> clauses = List.of(
                phaseClause(MarketPhase.Type.BULL_TREND, false),
                phaseClause(MarketPhase.Type.RANGE, true),
                phaseClause(MarketPhase.Type.BEAR_TREND, false),
                phaseClause(MarketPhase.Type.BEAR_TREND, true),
                phaseClause(MarketPhase.Type.BULL_TREND, false));

        assertThat(resolve(clauses)).isEqualTo(MarketPhase.Type.RANGE);
    }

    /** Первая ИСТИННАЯ, а не первая в списке. */
    @Test
    @DisplayName("U16.3 — истинна только последняя клауза: тип её собственный, BEAR_TREND")
    void u16_3_theFirstSatisfiedClauseWinsEvenIfItIsTheLastInTheList() {
        List<StrategyMarketPhaseRule> clauses = List.of(
                phaseClause(MarketPhase.Type.BULL_TREND, false),
                phaseClause(MarketPhase.Type.RANGE, false),
                phaseClause(MarketPhase.Type.BEAR_TREND, true));

        assertThat(resolve(clauses)).isEqualTo(MarketPhase.Type.BEAR_TREND);
    }

    /** Ни одна клауза не истинна — консервативный дефолт. */
    @Test
    @DisplayName("U16.4 — ни одна клауза не истинна: тип UNKNOWN")
    void u16_4_noSatisfiedClauseFallsToTheConservativeDefault() {
        List<StrategyMarketPhaseRule> clauses = List.of(
                phaseClause(MarketPhase.Type.BULL_TREND, false),
                phaseClause(MarketPhase.Type.RANGE, false));

        assertThat(resolve(clauses)).isEqualTo(MarketPhase.Type.UNKNOWN);
    }

    /** Пустой список клауз — тот же консервативный дефолт. */
    @Test
    @DisplayName("U16.5 — список клауз пуст: тип UNKNOWN, отказа нет")
    void u16_5_anEmptyClauseListFallsToTheConservativeDefault() {
        assertThatCode(() -> assertThat(resolve(List.of())).isEqualTo(MarketPhase.Type.UNKNOWN))
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    /** Список клауз не подан вовсе — тот же исход. */
    @Test
    @DisplayName("U16.6 — список клауз не подан вовсе: тип UNKNOWN, отказа нет")
    void u16_6_anAbsentClauseListFallsToTheConservativeDefault() {
        assertThatCode(() -> assertThat(resolve(null)).isEqualTo(MarketPhase.Type.UNKNOWN))
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    /**
     * Пустое условие истинно, и у первой клаузы это делает фазу безусловной:
     * последующие клаузы недостижимы. Названное следствие, а не свойство
     * резолвера (`.claude/work/backlog.md` §«Пустое условие истинно, и у
     * клаузы фазы это даёт безусловную фазу»).
     */
    @Test
    @DisplayName("U16.7 — у первой клаузы условие пусто: тип её собственный RANGE, вторая недостижима")
    void u16_7_anEmptyConditionOnTheFirstClauseMakesThePhaseUnconditional() {
        List<StrategyMarketPhaseRule> clauses = List.of(
                unconditionalClause(MarketPhase.Type.RANGE),
                phaseClause(MarketPhase.Type.BEAR_TREND, true));

        assertThat(resolve(clauses))
                .as("вторая клауза истинна и всё равно недостижима")
                .isEqualTo(MarketPhase.Type.RANGE);
    }

    /** Нужный клаузе вход в контексте отсутствует — клауза консервативно ложна. */
    @Test
    @DisplayName("U16.9 — клауза читает индикатор, которого в контексте нет: тип UNKNOWN, отказа нет")
    void u16_9_aClauseWhoseOperandIsAbsentIsConservativelyFalse() {
        assertThatCode(() -> assertThat(resolve(List.of(absentOperandClause(MarketPhase.Type.RANGE))))
                .isEqualTo(MarketPhase.Type.UNKNOWN))
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    /** Резолвер безгосударственен: второй вызов не зависит от первого. */
    @Test
    @DisplayName("U16.10 — два вызова подряд с одними и теми же клаузами и контекстом: тип одинаков")
    void u16_10_theResolverIsStatelessBetweenCalls() {
        List<StrategyMarketPhaseRule> clauses = List.of(
                phaseClause(MarketPhase.Type.BULL_TREND, false),
                phaseClause(MarketPhase.Type.RANGE, true));
        ConditionEvaluationContext context = context();

        MarketPhase.Type first = resolver.resolve(clauses, context);
        MarketPhase.Type second = resolver.resolve(clauses, context);

        assertThat(second).isEqualTo(first).isEqualTo(MarketPhase.Type.RANGE);
    }

    /** Выход резолвера — значение перечня; момента подтверждения у фазы нет вовсе. */
    @Test
    @DisplayName("U16.11 — базовая сборка: выход — значение перечня, а у модели фазы момента подтверждения нет")
    void u16_11_theResolverEmitsATypeAndThePhaseHasNoConfirmationMoment() {
        assertThat(resolve(List.of(phaseClause(MarketPhase.Type.RANGE, true)))).isInstanceOf(MarketPhase.Type.class);

        assertThat(fieldNames(MarketPhase.class))
                .as("сохранять нечего: ни момента, ни идентификатора у фазы нет")
                .containsExactly("instrumentId", "type");
    }

    // --- базовая сборка ----------------------------------------------------

    private MarketPhase.Type resolve(List<StrategyMarketPhaseRule> clauses) {
        return resolver.resolve(clauses, context());
    }

    private static ConditionEvaluationContext context() {
        return ConditionEvaluationContext.builder()
                .latestIndicators(Map.of())
                .previousIndicators(Map.of())
                .structures(Map.of())
                .price(new BigDecimal("100"))
                .build();
    }
}
