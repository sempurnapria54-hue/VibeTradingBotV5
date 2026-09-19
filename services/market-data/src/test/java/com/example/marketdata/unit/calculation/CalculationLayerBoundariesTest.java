package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.allCalculators;
import static com.example.marketdata.unit.calculation.CalcFixture.allFieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.emaParams;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldTypes;
import static com.example.marketdata.unit.calculation.CalcFixture.phaseClause;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketdata.domain.service.phase.MarketPhaseResolver;
import com.example.marketdata.domain.service.structure.MarketStructureResolver;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов у слоя — чего он не делает: группа `U17` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/components/IndicatorJob.md §Границы;
 * docs/components/MarketStructureResolver.md §Границы;
 * docs/components/MarketPhaseResolver.md §Границы;
 * docs/rules/market-data-freshness.md).
 *
 * <p><b>Клеймы этой группы читаются составом класса, а не прогоном
 * одного входа:</b> «ни базы, ни соседа, ни брокера, ни лога» —
 * утверждение обо <b>всех</b> входах сразу, и прогон одного входа его не
 * устанавливает (.claude/rules/measurement-commands.md). Поэтому предмет
 * наблюдения здесь — объявленные поля предмета и состав его результата.
 *
 * <p><b>Перечень предмета закрыт составом слоя:</b> восемь вычислителей,
 * резолвер структуры и резолвер фазы — десять классов, и каждое клеймо
 * перебирает все десять.
 *
 * <p>Клетка `U17.4` кода не получила, и причина не в молчании дома:
 * <b>такого входа не существует</b> — доменная свеча признака закрытости не
 * несёт вовсе (звено `Z1`), и «закрытости не проверяет» наблюдать нечем.
 */
class CalculationLayerBoundariesTest {

    /** Метки типов, по которым узнаётся поле-тропа к своей базе. */
    private static final List<String> PERSISTENCE_MARKERS =
            List.of("DataService", "Repository", "EntityManager", "DataSource", "JdbcTemplate");

    /** Метки типов, по которым узнаётся поле-тропа к соседу либо в брокер. */
    private static final List<String> INTEGRATION_MARKERS =
            List.of("RestClient", "WebClient", "KafkaTemplate", "Producer", "Publisher", "IntegrationService");

    /** В базу слой не пишет: тропы к ней нет ни у одного из десяти классов. */
    @Test
    @DisplayName("U17.1 — все десять классов слоя: ни одного поля-тропы к базе; результат только возвращается")
    void u17_1_theLayerDeclaresNoPathToItsOwnDatabase() {
        assertThat(layerFieldTypeNames())
                .as("сохраняет джоба, а не расчёт")
                .noneMatch(name -> PERSISTENCE_MARKERS.stream().anyMatch(name::contains));
    }

    /** Соседу слой не звонит и в брокер не публикует: тропы туда тоже нет. */
    @Test
    @DisplayName("U17.2 — все десять классов слоя: ни одного поля-тропы к соседу или в брокер")
    void u17_2_theLayerDeclaresNoPathToAPeerOrToTheBroker() {
        assertThat(layerFieldTypeNames())
                .as("публикация — дело реле владельца")
                .noneMatch(name -> INTEGRATION_MARKERS.stream().anyMatch(name::contains));
    }

    /**
     * В лог слой не пишет: логгера не держит ни один из десяти классов.
     * <b>Оговорка названа:</b> настоящий коллаборатор резолвера фазы —
     * вычислитель условий — логгер держит и предупреждает на неоцениваемом
     * типе правила (звено `Z2`); он и есть единственный писатель в лог на
     * всей тропе.
     */
    @Test
    @DisplayName("U17.3 — все десять классов слоя: логгера нет ни у одного; единственный писатель — вычислитель условий")
    void u17_3_theLayerHoldsNoLoggerAndItsOnlyWriterIsTheConditionEvaluator() {
        assertThat(layerClasses())
                .allSatisfy(type -> assertThat(allFieldNames(type))
                        .as("поля класса %s", type.getSimpleName())
                        .doesNotContain("log"));

        assertThat(allFieldNames(StrategyConditionEvaluator.class))
                .as("единственный писатель в лог на тропе резолвера фазы — его коллаборатор")
                .contains("log");
    }

    /** Свежесть слой не мерит и не помечает: её мерит потребитель своим сроком. */
    @Test
    @DisplayName("U17.5 — значение на баре трёхлетней давности: свежесть не проверена и не помечена ничем")
    void u17_5_theLayerNeitherChecksNorMarksFreshness() {
        List<IndicatorValue> values = allCalculators().getFirst()
                .calculate(1L, 2L, risingSeries(12), emaParams(3, null));

        assertThat(values).isNotEmpty().allSatisfy(value -> assertThat(value.getCandleTimestamp())
                .as("бар лежит в прошлом, и значение всё равно выдано")
                .isBefore(OffsetDateTime.now().minusYears(1L)));
        assertThat(fieldNames(IndicatorValue.class))
                .as("пометки свежести у значения нет вовсе")
                .containsExactly("id", "instrumentId", "indicatorConfigId", "candleTimestamp");
    }

    /** Идентичностей слой не заводит: на результате ровно то, что подано аргументом. */
    @Test
    @DisplayName("U17.6 — идентичности поданы пустыми: на значениях они пусты, выдуманных нет")
    void u17_6_theLayerInventsNoComputationIdentity() {
        List<IndicatorValue> values = allCalculators().getFirst()
                .calculate(null, null, risingSeries(12), emaParams(3, null));

        assertThat(values).isNotEmpty().allSatisfy(value -> {
            assertThat(value.getInstrumentId()).isNull();
            assertThat(value.getIndicatorConfigId()).isNull();
        });
    }

    /** Структура — факт, а не решение: ни сигнала, ни команды в её составе нет. */
    @Test
    @DisplayName("U17.7 — состав структуры: девять полей факта, ни сигнала, ни торгового решения")
    void u17_7_theStructureIsAFactAndCarriesNoTradingDecision() {
        assertThat(fieldNames(MarketStructure.class))
                .containsExactly("id", "instrumentId", "marketStructureConfigId", "type",
                        "windowStartAt", "windowEndAt", "confirmedAt", "levels", "breakoutEvent");
    }

    /** Резолвер фазы значений индикаторов и структуры не считает: свечей он не видит вовсе. */
    @Test
    @DisplayName("U17.8 — резолвер фазы: единственный коллаборатор — вычислитель условий, свечи в поверхность не входят")
    void u17_8_thePhaseResolverComputesNeitherIndicatorsNorStructure() {
        assertThat(fieldTypes(MarketPhaseResolver.class))
                .containsExactly(StrategyConditionEvaluator.class);

        assertThat(Arrays.stream(MarketPhaseResolver.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .toList())
                .as("свечи в поверхность резолвера фазы не входят")
                .doesNotContain(Candle.class);
    }

    /** Фаза не персистится и истории не накапливает: её выход — значение перечня. */
    @Test
    @DisplayName("U17.9 — фаза: ни идентификатора, ни истории; выход резолвера — значение перечня")
    void u17_9_thePhaseIsNeitherPersistedNorAccumulated() {
        assertThat(fieldNames(MarketPhase.class))
                .as("идентификатора у фазы нет: сохранять её некуда")
                .doesNotContain("id");
        assertThat(fieldTypes(MarketPhase.class))
                .as("коллекции у фазы нет: истории она не накапливает")
                .noneMatch(List.class::isAssignableFrom);

        MarketPhase.Type resolved = new MarketPhaseResolver(new StrategyConditionEvaluator())
                .resolve(List.of(phaseClause(MarketPhase.Type.RANGE, true)), ConditionEvaluationContext.builder()
                        .latestIndicators(Map.of())
                        .previousIndicators(Map.of())
                        .structures(Map.of())
                        .build());

        assertThat(resolved).isInstanceOf(MarketPhase.Type.class);
    }

    // --- перечень предмета -------------------------------------------------

    /** Десять классов слоя: восемь вычислителей плюс два резолвера. */
    private static List<Class<?>> layerClasses() {
        List<Class<?>> classes = new ArrayList<>();
        allCalculators().forEach(calculator -> classes.add(calculator.getClass()));
        classes.add(MarketStructureResolver.class);
        classes.add(MarketPhaseResolver.class);
        return classes;
    }

    private static Stream<String> layerFieldTypeNames() {
        return layerClasses().stream()
                .flatMap(type -> fieldTypes(type).stream())
                .map(Class::getSimpleName);
    }
}
