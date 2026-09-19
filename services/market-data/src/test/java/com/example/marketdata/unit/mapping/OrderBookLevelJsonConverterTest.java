package com.example.marketdata.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketdata.mapping.OrderBookLevelJsonConverter;
import com.example.testsupport.JsonbOverlayProbe;
import com.example.tradingbot.domain.model.trade.market_snapshot.OrderBookLevel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Навес уровней книги заявок: единственная форма предмета, у которой
 * пустота НЕ зеркальна, и единственная, чей маппер берётся без копии.
 *
 * <p>Кейсы — группа `U8`
 * (.claude/tests/cases/jsonb-overlay-roundtrip.md). Маппер здесь подаётся
 * явным входом: конвертер берёт его как есть, и форма строки зависит от
 * настроек ЧУЖОГО бина.
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
    @DisplayName("U8.4 — политика включения у формы ЧУЖАЯ: пустое число заявок едет ключом")
    void u8_4_theInclusionPolicyIsInheritedFromTheForeignBean() {
        String json = converter.levelsToJson(List.of(levelWithoutOrderCount()));

        assertThat(readTree(json).get(0).has("orderCount"))
                .as("форма навеса наследует умолчание контекста, а не непустые поля, "
                        + "как у шести остальных конвертеров")
                .isTrue();
        assertThat(readTree(json).get(0).get("orderCount").isNull()).isTrue();
    }

    @Test
    @DisplayName("U8.5 — правка настроек чужого бина меняет содержимое колонки")
    void u8_5_aChangeInTheForeignBeanChangesTheColumnContent() {
        OrderBookLevelJsonConverter onNonNull = new OrderBookLevelJsonConverter(
                beanAssemblyMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL));

        String json = onNonNull.levelsToJson(List.of(levelWithoutOrderCount()));

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
