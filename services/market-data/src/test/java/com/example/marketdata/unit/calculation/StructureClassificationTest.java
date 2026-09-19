package com.example.marketdata.unit.calculation;

import static java.util.Objects.isNull;

import static com.example.marketdata.unit.calculation.CalcFixture.monotoneDowntrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.monotoneUptrendWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.rangeWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.singleSwingLowWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.structureParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.structure.MarketStructureResolver;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Структура — классификация типа: группа `U12` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/MarketStructure.md §«Семантика классификации
 * (как считается)»; ветви входов —
 * docs/components/MarketStructureResolver.md §«Потребляет готовые скаляры,
 * не пересчитывает»).
 *
 * <p><b>Базовая сборка:</b> параметры со всеми числами; ряд, дающий не
 * менее двух свинг-максимумов и двух свинг-минимумов; скаляр эффективности
 * подан явно.
 *
 * <p><b>Геометрия окна и её исход выведены, а не подобраны.</b> Окно
 * восходящего тренда даёт растущие максимумы {@code 120,122,124,126} и
 * растущие минимумы {@code 85,87,89}; ширина его полосы —
 * {@code 38.86%} от середины, то есть шире допустимой, поэтому при
 * недостаточном скаляре тип выходит «неизвестно», а не «диапазон». Окно
 * диапазона даёт четыре пивота на {@code 110} и три на {@code 90}, ширину
 * {@code 20%} и геометрии тренда не имеет ни в одну сторону.
 *
 * <p>Клетка `U12.12` (геометрия тренда и признаки диапазона выполнены
 * одновременно) кода не получила: дом перечисляет оба исхода и старшинства
 * между ними не называет.
 */
class StructureClassificationTest {

    private final MarketStructureResolver resolver = new MarketStructureResolver();

    /** Растущие максимумы, растущие минимумы и достаточный скаляр — восходящий тренд. */
    @Test
    @DisplayName("U12.1 — максимумы и минимумы растут, скаляр 0.9 не ниже порога 0.3: тип UPTREND")
    void u12_1_risingPivotsWithASufficientScalarGiveAnUptrend() {
        assertThat(resolve(monotoneUptrendWindow(), "0.9", withThreshold("0.3")).getType())
                .isEqualTo(MarketStructure.Type.UPTREND);
    }

    /** Падающие максимумы, падающие минимумы и достаточный скаляр — нисходящий тренд. */
    @Test
    @DisplayName("U12.2 — максимумы и минимумы падают, скаляр 0.9 не ниже порога 0.3: тип DOWNTREND")
    void u12_2_fallingPivotsWithASufficientScalarGiveADowntrend() {
        assertThat(resolve(monotoneDowntrendWindow(), "0.9", withThreshold("0.3")).getType())
                .isEqualTo(MarketStructure.Type.DOWNTREND);
    }

    /** Чистый ход обязан доминировать над шумом: без этого тренд не признаётся. */
    @Test
    @DisplayName("U12.3 — геометрия тренда есть, скаляр 0.1 ниже порога 0.3: тренд не признан, тип UNKNOWN")
    void u12_3_anInsufficientScalarDeniesTheTrendAndFallsThroughToTheRangeCheck() {
        assertThat(resolve(monotoneUptrendWindow(), "0.1", withThreshold("0.3")).getType())
                .as("полоса окна шире допустимой, поэтому проверка диапазона тоже не проходит")
                .isEqualTo(MarketStructure.Type.UNKNOWN);
    }

    /** Порог записан нестрогим неравенством: граница включена. */
    @Test
    @DisplayName("U12.4 — скаляр 0.5 равен порогу 0.5: тренд признан, граница включена")
    void u12_4_theScalarEqualToTheThresholdStillRecognisesTheTrend() {
        assertThat(resolve(monotoneUptrendWindow(), "0.5", withThreshold("0.5")).getType())
                .isEqualTo(MarketStructure.Type.UPTREND);
    }

    /** Геометрии тренда нет, полоса в пределах, касаний достаточно — диапазон. */
    @Test
    @DisplayName("U12.5 — геометрии тренда нет, полоса 20% в пределах, касаний 4 и 3 при нужных 2: тип RANGE")
    void u12_5_aBandWithinTheLimitsAndEnoughTouchesGivesARange() {
        assertThat(resolve(rangeWindow(), "0.1", structureParams()).getType())
                .isEqualTo(MarketStructure.Type.RANGE);
    }

    /** Полоса уже минимальной — консервативный дефолт. */
    @Test
    @DisplayName("U12.6 — полоса 20% уже минимальной 25%: тип UNKNOWN")
    void u12_6_aBandNarrowerThanTheMinimumFallsToTheConservativeDefault() {
        MarketStructureParams params = structureParams();
        params.setMinRangeWidthPercents(new BigDecimal("25"));

        assertThat(resolve(rangeWindow(), "0.1", params).getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
    }

    /** Полоса шире максимальной — тот же консервативный дефолт. */
    @Test
    @DisplayName("U12.7 — полоса 20% шире максимальной 15%: тип UNKNOWN")
    void u12_7_aBandWiderThanTheMaximumFallsToTheConservativeDefault() {
        MarketStructureParams params = structureParams();
        params.setMaxRangeWidthPercents(new BigDecimal("15"));

        assertThat(resolve(rangeWindow(), "0.1", params).getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
    }

    /** Касаний к границе меньше требуемого — диапазон не признаётся. */
    @Test
    @DisplayName("U12.8 — требуемых касаний 5, у границ 4 и 3: тип UNKNOWN")
    void u12_8_tooFewTouchesDenyTheRange() {
        MarketStructureParams params = structureParams();
        params.setMinTouches(5);

        assertThat(resolve(rangeWindow(), "0.1", params).getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
    }

    /** Свинг-пивотов с одной стороны меньше двух — геометрия не определена. */
    @Test
    @DisplayName("U12.9 — свинг-минимум ровно один: тип UNKNOWN")
    void u12_9_asideWithFewerThanTwoPivotsLeavesTheGeometryUndefined() {
        assertThat(resolve(singleSwingLowWindow(), "0.9", withThreshold("0.3")).getType())
                .isEqualTo(MarketStructure.Type.UNKNOWN);
    }

    /** Вход не объявлен — расчёт идёт внутренним прокси по ценам закрытия окна. */
    @Test
    @DisplayName("U12.10 — скаляр эффективности не подан: прокси на монотонном ряде даёт единицу, тип UPTREND")
    void u12_10_anAbsentScalarFallsBackToTheInternalProxy() {
        assertThatCode(() -> assertThat(resolve(monotoneUptrendWindow(), null, withThreshold("0.3")).getType())
                .as("прокси считается по ценам закрытия окна, отказа нет")
                .isEqualTo(MarketStructure.Type.UPTREND))
                .doesNotThrowAnyException();
    }

    /**
     * Порог не задан — применяется провизорный дефолт резолвера.
     * <b>Число дефолта ожиданием не является:</b> дом объявляет численные
     * пороги хвостом пользователя и их значения провизорными, поэтому
     * наблюдается только то, что расчёт идёт и отказа нет.
     */
    @Test
    @DisplayName("U12.11 — порог эффективности параметрами не задан: расчёт идёт, тип UPTREND, отказа нет")
    void u12_11_anUnsetThresholdFallsBackToTheProvisionalDefault() {
        MarketStructureParams params = structureParams();

        assertThat(params.getTrendEfficiencyThreshold()).as("порог параметрами не задан").isNull();
        assertThatCode(() -> assertThat(resolve(monotoneUptrendWindow(), "0.9", params).getType())
                .isEqualTo(MarketStructure.Type.UPTREND))
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    // --- базовая сборка и отклонения ---------------------------------------

    private MarketStructure resolve(List<Candle> window, String efficiencyRatio, MarketStructureParams params) {
        BigDecimal efficiency = isNull(efficiencyRatio) ? null : new BigDecimal(efficiencyRatio);
        return resolver.resolve(window, efficiency, null, params);
    }

    private static MarketStructureParams withThreshold(String threshold) {
        MarketStructureParams params = structureParams();
        params.setTrendEfficiencyThreshold(new BigDecimal(threshold));
        return params;
    }
}
