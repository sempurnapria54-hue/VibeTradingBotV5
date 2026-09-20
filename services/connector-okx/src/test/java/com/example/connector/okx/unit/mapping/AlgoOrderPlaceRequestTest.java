package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.integration.external.api.model.okx.request.PlaceAlgoOrderOkxRequest;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Доменная условная заявка → запрос постановки — группа `U25`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/AlgoOrder.md §«`Domain AlgoOrder → request`»,
 * §«`conditionType → ordType` (OKX)» и §«OKX request mapping —
 * дополнения»).
 *
 * <p><b>Базовая сборка:</b> доменная {@code AlgoOrder} с клиентским
 * идентификатором, направлением покупки, размером, истинным признаком
 * «только уменьшать», типом условия «остановка убытка» и условием с
 * одной ногой остановки убытка; имя инструмента вторым аргументом.
 *
 * <p><b>Флаг рыночного исполнения ставится ПО НАЛИЧИЮ ноги, а не
 * безусловно — и это отличает форму от встроенной защиты:</b> у
 * условной заявки ног две, и флаг каждой ставится только тогда, когда у
 * неё есть уровень; поставленный при пустой ноге, он объявил бы
 * площадке ногу, которой нет.
 */
class AlgoOrderPlaceRequestTest {

    private final AlgoOrderMapper mapper = Mappers.algoOrder();

    private static AlgoOrder algoOrder() {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setInternalId("tb-9");
        algoOrder.setDirection(AlgoOrder.Direction.BUY);
        algoOrder.setSize(BigDecimal.ONE);
        algoOrder.setPositionReducingOnly(true);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                new Trigger(stopLoss(), null), null));
        return algoOrder;
    }

    private static TriggerPrice stopLoss() {
        TriggerPrice leg = new TriggerPrice();
        leg.setValue(new BigDecimal("90"));
        leg.setType(AlgoOrder.TriggerPriceType.MARK);
        return leg;
    }

    private static TriggerPrice takeProfit() {
        TriggerPrice leg = new TriggerPrice();
        leg.setValue(new BigDecimal("120"));
        leg.setType(AlgoOrder.TriggerPriceType.LAST);
        return leg;
    }

    private PlaceAlgoOrderOkxRequest request(AlgoOrder algoOrder) {
        return mapper.domainToPlaceRequest(algoOrder, OkxFixture.INSTRUMENT);
    }

    @Test
    @DisplayName("U25.1 — базовая сборка: константы, перенос и нога остановки убытка")
    void u25_1_theBaseAssemblyBuildsTheRequest() {
        PlaceAlgoOrderOkxRequest built = request(algoOrder());

        assertThat(built.getInstId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(built.getTdMode()).isEqualTo("isolated");
        assertThat(built.getPosSide()).isEqualTo("net");
        assertThat(built.getAlgoClOrdId()).isEqualTo("tb-9");
        assertThat(built.getSide()).isEqualTo("buy");
        assertThat(built.getOrdType()).isEqualTo("conditional");
        assertThat(built.getSz()).isEqualTo("1");
        assertThat(built.getReduceOnly()).isTrue();
        assertThat(built.getSlTriggerPx()).isEqualTo("90");
        assertThat(built.getSlTriggerPxType()).isEqualTo("mark");
        assertThat(built.getSlOrdPx()).isEqualTo("-1");
    }

    @Test
    @DisplayName("U25.2 — ветка фиксации прибыли пуста целиком, включая флаг")
    void u25_2_theTakeProfitBranchIsEmptyIncludingTheFlag() {
        PlaceAlgoOrderOkxRequest built = request(algoOrder());

        assertThat(built.getTpTriggerPx()).isNull();
        assertThat(built.getTpTriggerPxType()).isNull();
        assertThat(built.getTpOrdPx()).isNull();
    }

    @Test
    @DisplayName("U25.3 — трейлинговые поля пусты")
    void u25_3_theTrailingFieldsAreEmpty() {
        PlaceAlgoOrderOkxRequest built = request(algoOrder());

        assertThat(built.getCallbackRatio()).isNull();
        assertThat(built.getCallbackSpread()).isNull();
        assertThat(built.getActivePx()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "TAKE_PROFIT,conditional",
            "PARTIAL_STOP_LOSS,conditional",
            "PARTIAL_TAKE_PROFIT,conditional",
            "OCO_FULL,oco",
            "TRAILING_PERCENTS,move_order_stop",
            "TRAILING_VALUE,move_order_stop"})
    @DisplayName("U25.4-U25.9 — перечень типов условия переводится в тип условной заявки площадки")
    void u25_4_to_9_theConditionTypeBecomesTheSourceOrdType(AlgoOrder.ConditionType type,
                                                            String expected) {
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setConditionType(type);

        assertThat(request(algoOrder).getOrdType()).isEqualTo(expected);
    }

    /**
     * Переход обязательного типа условия не охраняет, и вход этот до него не доезжает —
     * тип проставляет расчётный слой. Кейс охраны второго рубежа.
     */
    @Test
    @DisplayName("U25.10 — пустой тип условия роняет разбор перечня")
    void u25_10_anEmptyConditionTypeBreaksTheSwitch() {
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setConditionType(null);

        assertThatThrownBy(() -> request(algoOrder)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("U25.11 — обе ноги: оба уровня, обе базы и оба флага")
    void u25_11_bothLegsCarryBothFlags() {
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setConditionType(AlgoOrder.ConditionType.OCO_FULL);
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.OCO_FULL,
                new Trigger(stopLoss(), takeProfit()), null));

        PlaceAlgoOrderOkxRequest built = request(algoOrder);

        assertThat(built.getSlTriggerPx()).isEqualTo("90");
        assertThat(built.getSlTriggerPxType()).isEqualTo("mark");
        assertThat(built.getSlOrdPx()).isEqualTo("-1");
        assertThat(built.getTpTriggerPx()).isEqualTo("120");
        assertThat(built.getTpTriggerPxType()).isEqualTo("last");
        assertThat(built.getTpOrdPx()).isEqualTo("-1");
    }

    /** Наличие объекта ноги флага не даёт, даёт только уровень. */
    @Test
    @DisplayName("U25.12 — нога фиксации прибыли без уровня флага не получает")
    void u25_12_aLegWithoutALevelGetsNoFlag() {
        TriggerPrice levelless = new TriggerPrice();
        levelless.setType(AlgoOrder.TriggerPriceType.LAST);
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                new Trigger(stopLoss(), levelless), null));

        PlaceAlgoOrderOkxRequest built = request(algoOrder);

        assertThat(built.getTpOrdPx()).isNull();
        assertThat(built.getTpTriggerPxType()).isEqualTo("last");
    }

    @Test
    @DisplayName("U25.13 — условия нет: все ноги и оба флага пусты, отказа нет")
    void u25_13_anAbsentConditionLeavesEverythingEmpty() {
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setCondition(null);

        PlaceAlgoOrderOkxRequest built = request(algoOrder);

        assertThat(built.getSlTriggerPx()).isNull();
        assertThat(built.getSlOrdPx()).isNull();
        assertThat(built.getTpTriggerPx()).isNull();
        assertThat(built.getTpOrdPx()).isNull();
    }

    @Test
    @DisplayName("U25.14 — ветки триггера нет: тот же исход")
    void u25_14_anAbsentTriggerBranchLeavesEverythingEmpty() {
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS, null, null));

        PlaceAlgoOrderOkxRequest built = request(algoOrder);

        assertThat(built.getSlTriggerPx()).isNull();
        assertThat(built.getSlOrdPx()).isNull();
        assertThat(built.getTpOrdPx()).isNull();
    }

    @Test
    @DisplayName("U25.15 — трейлинг долей: доля и цена активации, абсолютный шаг пуст")
    void u25_15_percentTrailingCarriesTheRatio() {
        TriggerPrice activation = new TriggerPrice();
        activation.setValue(new BigDecimal("110"));
        Trailing trailing = new Trailing(new BigDecimal("0.5"), null, activation, null);
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setConditionType(AlgoOrder.ConditionType.TRAILING_PERCENTS);
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.TRAILING_PERCENTS, null, trailing));

        PlaceAlgoOrderOkxRequest built = request(algoOrder);

        assertThat(built.getCallbackRatio()).isEqualTo("0.5");
        assertThat(built.getActivePx()).isEqualTo("110");
        assertThat(built.getCallbackSpread()).isNull();
    }

    @Test
    @DisplayName("U25.16 — трейлинг абсолютным шагом: шаг есть, доли нет")
    void u25_16_valueTrailingCarriesTheSpread() {
        Trailing trailing = new Trailing(null, new BigDecimal("2"), null, null);
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setConditionType(AlgoOrder.ConditionType.TRAILING_VALUE);
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.TRAILING_VALUE, null, trailing));

        PlaceAlgoOrderOkxRequest built = request(algoOrder);

        assertThat(built.getCallbackSpread()).isEqualTo("2");
        assertThat(built.getCallbackRatio()).isNull();
    }

    @Test
    @DisplayName("U25.17 — доменная продажа в словарь площадки")
    void u25_17_aSellDirectionBecomesTheSourceWord() {
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setDirection(AlgoOrder.Direction.SELL);

        assertThat(request(algoOrder).getSide()).isEqualTo("sell");
    }

    @Test
    @DisplayName("U25.18 — пустое направление даёт пустую сторону")
    void u25_18_anEmptyDirectionGivesAnEmptySide() {
        AlgoOrder algoOrder = algoOrder();
        algoOrder.setDirection(null);

        assertThat(request(algoOrder).getSide()).isNull();
    }

    /** Размер считает расчётный слой. */
    @Test
    @DisplayName("U25.19 — доли закрытия в форме запроса нет")
    void u25_19_thereIsNoCloseFractionField() {
        assertThat(PlaceAlgoOrderOkxRequest.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("closeFraction", "szRatio");
    }

    /** Выражение типа стои́т вне охраны и разыменовывает пустую заявку (звено `Z1`). */
    @Test
    @DisplayName("U25.20 — пустая условная заявка при непустом имени инструмента роняет переход")
    void u25_20_anEmptyAlgoOrderMeetsAnExpressionOutsideTheGuard() {
        assertThatThrownBy(() -> mapper.domainToPlaceRequest(null, OkxFixture.INSTRUMENT))
                .isInstanceOf(NullPointerException.class);

        assertThat(mapper.domainToPlaceRequest(null, null)).isNull();
    }
}
