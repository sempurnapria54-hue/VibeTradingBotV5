package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.rangeBreakoutDownWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.rangeBreakoutReturnWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.rangeBreakoutUpWindow;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.structureParams;
import static com.example.marketdata.unit.calculation.CalcFixture.unknownTypeBreakoutWindow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.structure.MarketStructureResolver;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Структура — событие подтверждённого пробоя: группа `U14` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/MarketStructure.md §«MarketBreakoutEvent
 * (раздел)» и пункт о пробое в §«Семантика классификации (как считается)»).
 *
 * <p><b>Базовая сборка:</b> параметры со всеми числами, включая буфер
 * {@code 1%} и число баров удержания {@code 2}; ряд, дающий подтверждённые
 * границы диапазона {@code 110} и {@code 90}. Порог пробоя вверх поэтому
 * равен {@code 111.1}, вниз — {@code 89.1}.
 *
 * <p><b>Пробит бывает выданный граничный уровень, и пробой ломает
 * структуру:</b> событие несёт тип сломанной границы, а сама структура
 * становится «неизвестно» без граничных уровней. Детекция читает хвост
 * окна: удержание, завершившееся раньше последнего бара, событием не
 * становится.
 *
 * <p>Клетка `U14.10` получила код с закрытием находки `M-5`: база процента
 * буфера — цена пробиваемого уровня, и дом называет её прямо.
 */
class StructureBreakoutTest {

    private final MarketStructureResolver resolver = new MarketStructureResolver();

    /** Хвост, закрытый выше верхней границы с запасом буфера, даёт событие вверх. */
    @Test
    @DisplayName("U14.1 — два бара хвоста закрыты выше 111.1: событие RANGE_HIGH/UP с ценой 110 и моментом бара 10")
    void u14_1_aTailHeldAboveTheUpperBoundaryWithTheBufferYieldsAnUpwardBreakout() {
        MarketBreakoutEvent event = resolve(rangeBreakoutUpWindow(), structureParams()).getBreakoutEvent();

        assertThat(event).isNotNull();
        assertThat(event.getBrokenLevelType())
                .as("тип сломанного уровня — тип выданной границы диапазона")
                .isEqualTo(MarketPriceLevel.Type.RANGE_HIGH);
        assertThat(event.getDirection()).isEqualTo(MarketBreakoutEvent.Direction.UP);
        assertThat(event.getLevelPrice()).isEqualByComparingTo("110");
        assertThat(event.getConfirmedAt()).isEqualTo(barAt(10));
    }

    /** Тот же хвост ниже нижней границы даёт событие вниз. */
    @Test
    @DisplayName("U14.2 — два бара хвоста закрыты ниже 89.1: событие RANGE_LOW/DOWN с ценой 90")
    void u14_2_aTailHeldBelowTheLowerBoundaryYieldsADownwardBreakout() {
        MarketBreakoutEvent event = resolve(rangeBreakoutDownWindow(), structureParams()).getBreakoutEvent();

        assertThat(event).isNotNull();
        assertThat(event.getBrokenLevelType()).isEqualTo(MarketPriceLevel.Type.RANGE_LOW);
        assertThat(event.getDirection()).isEqualTo(MarketBreakoutEvent.Direction.DOWN);
        assertThat(event.getLevelPrice()).isEqualByComparingTo("90");
    }

    /** Удержание требуется на КАЖДОМ баре хвоста. */
    @Test
    @DisplayName("U14.3 — бар 9 хвоста закрыт на 109, внутри полосы: события нет, тип по-прежнему RANGE")
    void u14_3_aSingleTailBarThatDidNotHoldCancelsTheEvent() {
        List<Candle> window = rangeBreakoutUpWindow();
        window.get(9).setClose(new BigDecimal("109"));

        MarketStructure structure = resolve(window, structureParams());

        assertThat(structure.getBreakoutEvent()).isNull();
        assertThat(structure.getType())
                .as("прочие поля структуры не меняются")
                .isEqualTo(MarketStructure.Type.RANGE);
        assertThat(priceOf(structure, MarketPriceLevel.Type.RANGE_HIGH)).isEqualByComparingTo("110");
        assertThat(priceOf(structure, MarketPriceLevel.Type.RANGE_LOW)).isEqualByComparingTo("90");
    }

    /** Запас буфера обязателен наравне с удержанием. */
    @Test
    @DisplayName("U14.4 — хвост закрыт на 110.5 и 110.8, выше уровня но ниже порога 111.1: события нет")
    void u14_4_aTailAboveTheLevelButBelowTheBufferYieldsNoEvent() {
        List<Candle> window = rangeBreakoutUpWindow();
        window.get(9).setClose(new BigDecimal("110.5"));
        window.get(10).setClose(new BigDecimal("110.8"));

        assertThat(resolve(window, structureParams()).getBreakoutEvent()).isNull();
    }

    /** Окно короче числа баров удержания — событию неоткуда взяться. */
    @Test
    @DisplayName("U14.5 — баров удержания 20 при окне из 11: события нет, отказа нет")
    void u14_5_aWindowShorterThanTheConfirmationRunYieldsNoEvent() {
        MarketStructureParams params = structureParams();
        params.setBreakoutConfirmationBars(20);

        assertThatCode(() -> assertThat(resolve(rangeBreakoutUpWindow(), params).getBreakoutEvent()).isNull())
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    /** Удержание требует хотя бы одного бара. */
    @Test
    @DisplayName("U14.6 — баров удержания 0: события нет, отказа нет")
    void u14_6_aConfirmationRunBelowOneBarYieldsNoEvent() {
        MarketStructureParams params = structureParams();
        params.setBreakoutConfirmationBars(0);

        assertThatCode(() -> assertThat(resolve(rangeBreakoutUpWindow(), params).getBreakoutEvent()).isNull())
                .as("отказа нет")
                .doesNotThrowAnyException();
    }

    /**
     * Подтверждённый пробой ломает структуру окна: тип «неизвестно»,
     * граничных уровней нет, свинги сохранены, момент подтверждения каркаса
     * пуст — момент несёт событие. Тренда пробой не объявляет.
     */
    @Test
    @DisplayName("U14.7 — подтверждённый пробой на диапазоне: тип UNKNOWN, границ нет, свинги и событие на месте")
    void u14_7_aConfirmedBreakoutBreaksTheStructureOfTheWindow() {
        MarketStructure structure = resolve(rangeBreakoutUpWindow(), structureParams());

        assertThat(structure.getBreakoutEvent()).as("предпосылка кейса — событие есть").isNotNull();
        assertThat(structure.getBreakoutEvent().getBrokenLevelType())
                .as("событие несёт сломанную границу")
                .isEqualTo(MarketPriceLevel.Type.RANGE_HIGH);
        assertThat(structure.getType())
                .as("подтверждённый пробой уводит в «неизвестно», а не в тренд")
                .isEqualTo(MarketStructure.Type.UNKNOWN);
        assertThat(structure.getConfirmedAt()).as("у каркаса подтверждать нечего").isNull();
        assertThat(structure.getLevels())
                .as("свинги сохранены, граничных уровней нет: четыре максимума и четыре минимума")
                .hasSize(8)
                .allSatisfy(level -> assertThat(level.getType())
                        .isIn(MarketPriceLevel.Type.SWING_HIGH, MarketPriceLevel.Type.SWING_LOW));
    }

    /**
     * Пробит бывает <b>выданный граничный уровень</b>; у консервативного
     * исхода граничных уровней нет, значит ломать нечего, хотя хвост закрыт
     * за крайним пивотом окна.
     */
    @Test
    @DisplayName("U14.8 — тип UNKNOWN, хвост закрыт за крайним пивотом: события нет, ломать нечего")
    void u14_8_aConservativeOutcomeHasNoConfirmedLevelToBreak() {
        MarketStructure structure = resolve(unknownTypeBreakoutWindow(), structureParams());

        assertThat(structure.getType()).as("предпосылка кейса — тип UNKNOWN")
                .isEqualTo(MarketStructure.Type.UNKNOWN);
        assertThat(structure.getBreakoutEvent())
                .as("подтверждённых уровней у консервативного исхода не объявлено")
                .isNull();
    }

    /**
     * Событие мерит удержание <b>на хвосте окна</b>: пробой, после которого
     * цена вернулась внутрь, к моменту прохода ложный. Бар выхода {@code 10}
     * пивотом не стал — сосед справа выше, — поэтому крайний пивот потолка
     * и с ним граница {@code 110} остаются на месте.
     */
    @Test
    @DisplayName("U14.9 — удержание завершилось на баре 10, бар 11 вернулся внутрь: события нет, RANGE 110/90")
    void u14_9_aBreakoutFollowedByAReturnInsideIsNoEvent() {
        MarketStructure structure = resolve(rangeBreakoutReturnWindow(), structureParams());

        assertThat(structure.getBreakoutEvent())
                .as("удержания на хвосте окна нет — пробой с возвратом ложный")
                .isNull();
        assertThat(structure.getType())
                .as("граница окна не сдвинута, пока пивот выхода не подтверждён")
                .isEqualTo(MarketStructure.Type.RANGE);
        assertThat(priceOf(structure, MarketPriceLevel.Type.RANGE_HIGH)).isEqualByComparingTo("110");
        assertThat(priceOf(structure, MarketPriceLevel.Type.RANGE_LOW)).isEqualByComparingTo("90");
    }

    /**
     * Процент буфера берётся от цены уровня, а не от ширины полосы. У
     * базовой сборки полоса {@code 20} при цене сопротивления {@code 110}:
     * буфер {@code 1%} от полосы дал бы порог {@code 110.2}, от цены — даёт
     * {@code 111.1}. Хвост, закрытый между двумя порогами, разводит базы:
     * событие было бы при первой и не бывает при второй.
     *
     * <p>Меняются только закрытия хвоста: пивоты строятся по максимумам и
     * минимумам, поэтому границы {@code 110} и {@code 90} остаются на месте.
     */
    @Test
    @DisplayName("U14.10 — хвост закрыт на 110.5 и 111.0, между порогом от полосы 110.2 и от цены 111.1: события нет")
    void u14_10_theBufferPercentIsTakenFromTheLevelPriceAndNotFromTheBandWidth() {
        List<Candle> betweenThresholds = rangeBreakoutUpWindow();
        betweenThresholds.get(9).setClose(new BigDecimal("110.5"));
        betweenThresholds.get(10).setClose(new BigDecimal("111.0"));
        List<Candle> abovePriceThreshold = rangeBreakoutUpWindow();
        abovePriceThreshold.get(9).setClose(new BigDecimal("111.2"));
        abovePriceThreshold.get(10).setClose(new BigDecimal("111.3"));

        assertThat(resolve(betweenThresholds, structureParams()).getBreakoutEvent())
                .as("запас от ширины полосы пройден, от цены уровня — нет")
                .isNull();
        assertThat(resolve(abovePriceThreshold, structureParams()).getBreakoutEvent())
                .as("запас от цены уровня пройден на обоих барах удержания")
                .isNotNull();
    }

    /**
     * Пивотов нет вовсе — границ нет, и ломать нечего, хотя окно и параметры
     * в порядке. <b>Клетка добрана под-шагом 3</b> по пробелу `G4` документа:
     * `U11.6` наблюдает тип, `U15.1` и `U15.2` — пустое окно и пустые
     * параметры, а ветвь «границ нет при исправном входе» не была взята.
     * Строго растущий ряд пивотов не даёт по построению: у каждого
     * внутреннего бара сосед справа выше, а сосед слева ниже.
     */
    @Test
    @DisplayName("U14.11 — пивотов нет вовсе при исправных окне и параметрах: события нет, тип UNKNOWN, отказа нет")
    void u14_11_aWindowWithoutPivotsHasNoBoundaryToBreak() {
        assertThatCode(() -> {
            MarketStructure structure = resolve(risingSeries(9), structureParams());

            assertThat(structure.getLevels()).isEmpty();
            assertThat(structure.getType()).isEqualTo(MarketStructure.Type.UNKNOWN);
            assertThat(structure.getBreakoutEvent()).isNull();
            assertThat(structure.getWindowEndAt()).isEqualTo(barAt(8));
        }).as("отказа нет").doesNotThrowAnyException();
    }

    // --- базовая сборка ----------------------------------------------------

    private MarketStructure resolve(List<Candle> window, MarketStructureParams params) {
        return resolver.resolve(window, new BigDecimal("0.1"), null, params);
    }

    private static BigDecimal priceOf(MarketStructure structure, MarketPriceLevel.Type type) {
        MarketPriceLevel level = structure.findLevel(type);
        assertThat(level).as("уровень типа %s", type).isNotNull();
        return level.getPrice();
    }
}
