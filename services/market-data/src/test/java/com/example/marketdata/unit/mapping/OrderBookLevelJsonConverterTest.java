package com.example.marketdata.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketdata.mapping.OrderBookLevelJsonConverter;
import com.example.testsupport.JsonbOverlayProbe;
import com.example.tradingbot.domain.model.trade.market_snapshot.OrderBookLevel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Навес уровней книги заявок: единственная форма предмета, у которой
 * пустота НЕ зеркальна.
 *
 * <p>Кейсы — группа `U8` и клетки `U11.7`, `U13.4`
 * (.claude/tests/cases/jsonb-overlay-roundtrip.md). Маппер подаётся явным
 * входом: `U8.5` меряет, что политику включения конвертер пинит сам, и
 * правка настроек чужого бина форму строки не двигает.
 */
class OrderBookLevelJsonConverterTest extends JsonbOverlayProbe {

    private final OrderBookLevelJsonConverter converter =
            new OrderBookLevelJsonConverter(beanAssemblyMapper());

    @Test
    @DisplayName("U8.1 — состав и порядок уровней переживают запись")
    void u8_1_theCompositionAndOrderOfLevelsSurviveTheWrite() {
        List<OrderBookLevel> levels = levels();

        List<OrderBookLevel> read = converter.jsonToLevels(converter.levelsToJson(levels));

        assertThat(read).usingRecursiveComparison().isEqualTo(levels);
        assertThat(read).extracting(OrderBookLevel::getPrice)
                .containsExactly(new BigDecimal("30000.1"), new BigDecimal("30000.2"),
                        new BigDecimal("30000.3"));
    }

    @Test
    @DisplayName("U8.2 — пусто на записи даёт ПУСТОЙ МАССИВ, а не пустоту")
    void u8_2_anEmptyValueOnWriteYieldsAnEmptyArray() {
        assertThat(converter.levelsToJson(null))
                .as("колонка стороны среза объявлена обязательной, и пустота в неё не легла бы")
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("U8.3 — пусто на чтении даёт ПУСТОЙ СПИСОК, а не пустоту")
    void u8_3_anEmptyValueOnReadYieldsAnEmptyList() {
        assertThat(converter.jsonToLevels(null)).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U8.4 — пустое число заявок в строку не пишется")
    void u8_4_anEmptyOrderCountIsNotWritten() {
        String json = converter.levelsToJson(List.of(levelWithoutOrderCount()));

        assertThat(readTree(json).get(0).has("orderCount"))
                .as("политика непустых полей у формы своя, как у шести остальных конвертеров")
                .isFalse();
        assertThat(converter.jsonToLevels(json)).singleElement()
                .extracting(OrderBookLevel::getOrderCount).isNull();
    }

    @Test
    @DisplayName("U8.5 — обратная политика у чужого бина содержимого колонки не меняет")
    void u8_5_theOppositePolicyOfTheForeignBeanDoesNotChangeTheColumnContent() {
        OrderBookLevelJsonConverter onAlways = new OrderBookLevelJsonConverter(
                beanAssemblyMapper().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS));

        String json = onAlways.levelsToJson(List.of(levelWithoutOrderCount()));

        assertThat(json).isEqualTo(converter.levelsToJson(List.of(levelWithoutOrderCount())));
        assertThat(readTree(json).get(0).has("orderCount")).isFalse();
    }

    @Test
    @DisplayName("U8.6 — неразбираемая строка: свой класс, сторона в сообщении, причина сохранена")
    void u8_6_anUnparseableStringFailsWithTheOwnClassAndKeepsTheCause() {
        assertThatThrownBy(() -> converter.jsonToLevels("[{\"price\":1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order book levels")
                .hasMessageContaining("deserialization")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("U8.7 — объект вместо массива отказывает несовпадением формы входа")
    void u8_7_anObjectInsteadOfAnArrayFails() {
        assertThatThrownBy(() -> converter.jsonToLevels("{\"price\":1}"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("U11.7 — на строгом источнике лишний ключ своей строки отброшен, прочее тождественно")
    void u11_7_onAStrictSourceAnUnknownKeyOfTheOwnRowIsDropped() {
        ObjectMapper strict = beanAssemblyMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        List<OrderBookLevel> levels = levels();
        String withUnknown = converter.levelsToJson(levels)
                .replaceFirst("\\{", "{\"liquidatedOrders\":0,");

        assertThat(new OrderBookLevelJsonConverter(strict).jsonToLevels(withUnknown))
                .as("U11.7: вход %s, источник строгий — терпимость запинена конвертером", withUnknown)
                .usingRecursiveComparison().isEqualTo(levels);
    }

    @Test
    @DisplayName("U13.4 — уровень со всеми полями: ключи строки равны литеральному перечню")
    void u13_4_theRowKeysOfAFullLevelEqualTheLiteralList() {
        String json = converter.levelsToJson(levels());

        assertThat(keysOf(readTree(json).get(0).toString()))
                .as("U13.4: уровень книги заявок")
                .containsExactlyInAnyOrder("price", "size", "orderCount");
    }

    private static List<OrderBookLevel> levels() {
        return List.of(
                new OrderBookLevel(new BigDecimal("30000.1"), new BigDecimal("1.5"), 3),
                new OrderBookLevel(new BigDecimal("30000.2"), new BigDecimal("0.7"), 1),
                new OrderBookLevel(new BigDecimal("30000.3"), new BigDecimal("2.2"), 5));
    }

    private static OrderBookLevel levelWithoutOrderCount() {
        return new OrderBookLevel(new BigDecimal("30000.1"), new BigDecimal("1.5"), null);
    }
}
