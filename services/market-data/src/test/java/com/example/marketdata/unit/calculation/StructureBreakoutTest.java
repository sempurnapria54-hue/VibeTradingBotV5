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
import org.junit.jupiter.api.Tag;
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
 * <p><b>Три клетки красны по построению:</b> `U14.7` предъявляет находку
 * `M-2` (подтверждённый пробой структуру не переклассифицирует и считается
 * на любом типе), `U14.8` — её же вторую половину, `U14.9` — находки `M-4`
 * и `M-9` (детекция читает только хвост окна). Они помечены меткой
 * {@code debt}.
 *
 * <p>Клетка `U14.10` кода не получила: от чего берётся процент буфера — от
 * ширины полосы или от цены уровня, — два носителя называют по-разному
 * (`M-5`).
 */
class StructureBreakoutTest {

    private final MarketStructureResolver resolver = new MarketStructureResolver();

    /** Хвост, закрытый выше сопротивления с запасом буфера, даёт событие вверх. */
    @Test
    @DisplayName("U14.1 — два бара хвоста закрыты выше 111.1: событие RESISTANCE/UP с ценой 110 и моментом бара 10")
    void u14_1_aTailHeldAboveTheResistanceWithTheBufferYieldsAnUpwardBreakout() {
        MarketBreakoutEvent event = resolve(rangeBreakoutUpWindow(), structureParams()).getBreakoutEvent();

        assertThat(event).isNotNull();
        assertThat(event.getBrokenLevelType()).isEqualTo(MarketPriceLevel.Type.RESISTANCE);
        assertThat(event.getDirection()).isEqualTo(MarketBreakoutEvent.Direction.UP);
        assertThat(event.getLevelPrice()).isEqualByComparingTo("110");
        assertThat(event.getConfirmedAt()).isEqualTo(barAt(10));
    }

    /** Тот же хвост ниже поддержки даёт событие вниз. */
    @Test
    @DisplayName("U14.2 — два бара хвоста закрыты ниже 89.1: событие SUPPORT/DOWN с ценой 90")
    void u14_2_aTailHeldBelowTheSupportYieldsADownwardBreakout() {
        MarketBreakoutEvent event = resolve(rangeBreakoutDownWindow(), structureParams()).getBreakoutEvent();

        assertThat(event).isNotNull();
        assertThat(event.getBrokenLevelType()).isEqualTo(MarketPriceLevel.Type.SUPPORT);
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
        assertThat(structure.getLevels())
                .hasSameSizeAs(resolve(rangeBreakoutUpWindow(), structureParams()).getLevels());
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
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * объявляет, что подтверждённый пробой переклассифицирует структуру;
     * код считает событие <b>после</b> классификации и типа не меняет.
     * Красный прогон предъявляет находку `M-2` (`.claude/work/backlog.md`
     * §«Подтверждённый пробой структуру не переклассифицирует и считается
     * на любом типе»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U14.7 — подтверждённый пробой на диапазоне: тип переклассифицирован, а не остался RANGE")
    void u14_7_aConfirmedBreakoutReclassifiesTheStructureAwayFromTheRange() {
        MarketStructure structure = resolve(rangeBreakoutUpWindow(), structureParams());

        assertThat(structure.getBreakoutEvent()).as("предпосылка кейса — событие есть").isNotNull();
        assertThat(structure.getType())
                .as("подтверждённый пробой уводит из диапазона")
                .isNotEqualTo(MarketStructure.Type.RANGE);
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Пробит
     * бывает <b>уровень</b>, а уровень — подтверждённый; у консервативного
     * исхода подтверждённых уровней не объявлено, значит ломать нечего. Код
     * считает событие на любом типе. Красный прогон предъявляет ту же
     * находку `M-2`.
     */
    @Test
    @Tag("debt")
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
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * объявляет уровень сломанным, как только цена закрылась за ним с
     * запасом и продержалась требуемое число баров; код читает только
     * <b>хвост</b> окна, поэтому удержание, завершившееся раньше последнего
     * бара, событием не становится вовсе. Красный прогон предъявляет находки
     * `M-4` и `M-9` (`.claude/work/backlog.md` §«Пробой обнаруживается
     * только хвостом окна»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U14.9 — удержание завершилось на баре 10, дальше возврат внутрь: событие есть с моментом бара 10")
    void u14_9_aBreakoutThatEndedBeforeTheWindowEndIsStillAnEvent() {
        MarketStructure structure = resolve(rangeBreakoutReturnWindow(), structureParams());

        assertThat(structure.getBreakoutEvent())
                .as("уровень жив, пока его не сломал подтверждённый пробой")
                .isNotNull();
        assertThat(structure.getBreakoutEvent().getConfirmedAt())
                .as("момент подтверждения — бар, на котором удержание завершилось")
                .isEqualTo(barAt(10));
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
}
