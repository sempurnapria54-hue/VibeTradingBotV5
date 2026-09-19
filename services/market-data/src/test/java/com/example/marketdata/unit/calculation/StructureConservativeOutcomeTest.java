package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.monotoneUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.rangeWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.spreadClusterWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.structureParams;
import static com.example.marketdata.unit.calculation.CalcFixture.zeroPriceWindow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.structure.MarketStructureResolver;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Структура — консервативный исход и ветви входов: группа `U15` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/MarketStructure.md §«Семантика классификации
 * (как считается)»; docs/components/MarketStructureResolver.md §Границы;
 * docs/components/MarketStructureJob.md §«Объявленный вход не готов —
 * консервативный исход»).
 *
 * <p><b>Базовая сборка:</b> та же, что у группы `U12`; наблюдается исход на
 * неполном входе.
 *
 * <p><b>Неполнота — не отказ, а «неизвестно».</b> Пустое окно, неполные
 * параметры и вырожденная полоса дают консервативный тип без исключения;
 * необъявленные скаляры уводят расчёт на свои ветви, а не роняют его.
 */
class StructureConservativeOutcomeTest {

    private final MarketStructureResolver resolver = new MarketStructureResolver();

    /** Пустое окно: считать нечего, и каркас остаётся без границ. */
    @Test
    @DisplayName("U15.1 — окно свечей пусто: тип UNKNOWN, уровней нет, границ окна нет, события нет, отказа нет")
    void u15_1_anEmptyWindowLeavesEvenTheFrameWithoutBounds() {
        assertThatCode(() -> {
            MarketStructure structure = resolver.resolve(List.of(), null, null, structureParams());

            assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
            assertThat(structure.getLevels()).isEmpty();
            assertThat(structure.getWindowStartAt()).isNull();
            assertThat(structure.getWindowEndAt()).isNull();
            assertThat(structure.getConfirmedAt()).isNull();
            assertThat(structure.getBreakoutEvent()).isNull();
        }).as("отказа нет").doesNotThrowAnyException();
    }

    /** Параметров нет вовсе: границы окна выводятся из свечей, а не из параметров. */
    @Test
    @DisplayName("U15.2 — параметры не поданы вовсе: тип UNKNOWN, границы окна проставлены, уровней и события нет")
    void u15_2_absentParamsStillLeaveTheWindowBoundsInPlace() {
        assertThatCode(() -> {
            MarketStructure structure = resolver.resolve(rangeWindow(), null, null, null);

            assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
            assertThat(structure.getWindowStartAt()).isEqualTo(barAt(0));
            assertThat(structure.getWindowEndAt()).isEqualTo(barAt(8));
            assertThat(structure.getLevels()).isEmpty();
            assertThat(structure.getBreakoutEvent()).isNull();
        }).as("отказа нет").doesNotThrowAnyException();
    }

    /** Одно обязательное число пусто — расчёт не определён, а не частично выполнен. */
    @Test
    @DisplayName("U15.3 — требуемых касаний нет в параметрах: тот же исход, что у непереданных параметров")
    void u15_3_oneMissingRequiredNumberIsAsGoodAsNoParamsAtAll() {
        MarketStructureParams params = structureParams();
        params.setMinTouches(null);

        MarketStructure structure = resolver.resolve(rangeWindow(), null, null, params);

        assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
        assertThat(structure.getWindowStartAt()).isEqualTo(barAt(0));
        assertThat(structure.getLevels())
                .as("частичного результата не бывает: расчёт не определён")
                .isEmpty();
        assertThat(structure.getBreakoutEvent()).isNull();
    }

    /** Скаляр волатильности не объявлен — толеранс берётся долей цены, и расчёт идёт. */
    @Test
    @DisplayName("U15.4 — скаляр волатильности не подан: толеранс долей цены, расчёт идёт, тип RANGE")
    void u15_4_anAbsentVolatilityScalarFallsBackToAFractionOfThePrice() {
        assertThatCode(() -> assertThat(resolver.resolve(rangeWindow(), new BigDecimal("0.1"), null,
                structureParams()).getType())
                .isEqualTo(MarketStructure.Type.RANGE))
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    /** Множитель толеранса не задан — применяется провизорный дефолт, и расчёт идёт. */
    @Test
    @DisplayName("U15.5 — множитель толеранса не задан, скаляр волатильности 1: расчёт идёт, тип RANGE")
    void u15_5_anUnsetToleranceMultiplierFallsBackToTheProvisionalDefault() {
        MarketStructureParams params = structureParams();

        assertThat(params.getLevelToleranceAtrMultiplier()).as("множитель параметрами не задан").isNull();
        assertThatCode(() -> assertThat(resolver.resolve(rangeWindow(), new BigDecimal("0.1"),
                BigDecimal.ONE, params).getType())
                .isEqualTo(MarketStructure.Type.RANGE))
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    /** Нулевая середина полосы — диапазон не признаётся, и деления на ноль не происходит. */
    @Test
    @DisplayName("U15.6 — все цены окна нулевые, середина полосы 0: тип UNKNOWN, деления на ноль нет")
    void u15_6_aZeroBandMidpointDeniesTheRangeWithoutDividingByZero() {
        assertThatCode(() -> assertThat(resolver.resolve(zeroPriceWindow(), new BigDecimal("0.9"), null,
                structureParams()).getType())
                .isEqualTo(MarketStructure.Type.UNKNOWN))
                .as("деления на ноль не происходит")
                .doesNotThrowAnyException();
    }

    /** Идентичности на каркасе ставит джоба: результат резолвер не сохраняет. */
    @Test
    @DisplayName("U15.7 — базовая сборка: ни идентификатора, ни инструмента, ни идентичности вычисления на каркасе")
    void u15_7_theFrameCarriesNoIdentityOfItsOwn() {
        MarketStructure structure = resolver.resolve(rangeWindow(), new BigDecimal("0.1"), null, structureParams());

        assertThat(structure.getId()).isNull();
        assertThat(structure.getInstrumentId()).isNull();
        assertThat(structure.getMarketStructureConfigId()).isNull();
    }

    /**
     * Объявленный вход, поданный пустым из-за устаревания, неотличим от
     * необъявленного: у резолвера нет операнда, который различал бы эти два
     * состояния, — различие держит джоба.
     */
    @Test
    @DisplayName("U15.8 — объявленный вход подан пустым: та же ветвь, что у необъявленного; признака различия нет")
    void u15_8_aDeclaredButStaleInputTakesTheSameBranchAsAnUndeclaredOne() {
        assertThat(resolver.resolve(monotoneUptrendWindow(), null, null, withThreshold("0.3")).getType())
                .as("пустой скаляр уводит на ветвь внутреннего прокси")
                .isEqualTo(MarketStructure.Type.UPTREND);

        assertThat(parameterTypesOfResolve())
                .as("признака «объявлен, но не готов» в поверхности резолвера нет")
                .containsExactly(List.class, BigDecimal.class, BigDecimal.class, MarketStructureParams.class);
    }

    /** Момент подтверждения выводится из бара: своих часов резолвер не читает. */
    @Test
    @DisplayName("U15.9 — базовая сборка: момент подтверждения равен моменту бара 8 и лежит в прошлом")
    void u15_9_theConfirmationMomentComesFromTheBarAndNotFromAClock() {
        MarketStructure structure = resolver.resolve(rangeWindow(), new BigDecimal("0.1"), null, structureParams());

        assertThat(structure.getConfirmedAt()).isEqualTo(barAt(8));
        assertThat(structure.getConfirmedAt())
                .as("свежести резолвер не проверяет: момент взят из данных")
                .isBefore(OffsetDateTime.now());
    }

    /**
     * Две ветви толеранса дают <b>разный счёт касаний</b>, а с ним и разный
     * исход классификации. <b>Клетка добрана под-шагом 3</b> по пробелу `G5`
     * документа: `U15.4` и `U15.5` проверяли, что расчёт идёт по каждой
     * ветви, но не то, что ветви расходятся.
     *
     * <p>Окно выведено под этот предмет: пивоты потолка стоя́т на
     * {@code 110} и {@code 109}. Толеранс долей цены равен {@code 0.55} и
     * второго пивота не захватывает — касание одно при требуемых двух;
     * толеранс от скаляра волатильности {@code 4} при дефолтном множителе
     * равен {@code 2} и захватывает оба.
     */
    @Test
    @DisplayName("U15.10 — кластер разнесён: толеранс долей цены даёт UNKNOWN, толеранс от скаляра — RANGE")
    void u15_10_theTwoToleranceBranchesGiveDifferentTouchCountsAndOutcomes() {
        assertThat(resolver.resolve(spreadClusterWindow(), new BigDecimal("0.1"), null, structureParams()).getType())
                .as("доля цены второго пивота не захватывает: касание одно при требуемых двух")
                .isEqualTo(MarketStructure.Type.UNKNOWN);

        assertThat(resolver.resolve(spreadClusterWindow(), new BigDecimal("0.1"), new BigDecimal("4"),
                structureParams()).getType())
                .as("толеранс от скаляра волатильности захватывает оба пивота")
                .isEqualTo(MarketStructure.Type.RANGE);
    }

    // --- отклонения от базовой сборки --------------------------------------

    private static MarketStructureParams withThreshold(String threshold) {
        MarketStructureParams params = structureParams();
        params.setTrendEfficiencyThreshold(new BigDecimal(threshold));
        return params;
    }

    private static List<Object> parameterTypesOfResolve() {
        return Arrays.stream(MarketStructureResolver.class.getDeclaredMethods())
                .filter(method -> "resolve".equals(method.getName()))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .map(Object.class::cast)
                .toList();
    }
}
