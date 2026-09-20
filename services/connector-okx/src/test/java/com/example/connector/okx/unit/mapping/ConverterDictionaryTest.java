package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.exception.ExternalInvariantViolationException;
import com.example.connector.okx.exception.ExternalStatusException;
import com.example.connector.okx.mapping.OkxResponseConverter;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Конвертер: словари площадки и перечни — группа `U6` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (`.claude/rules/codestyle.md` §«Слои (зоны ответственности)»:
 * доменный перечень внутрь площадки не уезжает, литерал площадки в
 * домен не попадает, и граница проходит здесь).
 *
 * <p><b>Базовая сборка:</b> та же, что у `U5`. Вход — либо литерал
 * площадки, либо доменное значение перечня.
 *
 * <p>Исходов у перевода словаря площадки в домен <b>три</b>: пустота
 * (поля нет — обновления не происходит), значение и отказ (поле есть,
 * значение неизвестно). Второй и третий у разных полей решены
 * по-разному, и различие названо: у стороны заявки неизвестное значение
 * отказывает, у ценовой базы триггера — даёт пустоту.
 */
class ConverterDictionaryTest {

    private final OkxResponseConverter converter = new OkxResponseConverter();

    @Test
    @DisplayName("U6.1 — доменная покупка в словарь площадки")
    void u6_1_domainBuyBecomesTheSourceWord() {
        assertThat(converter.orderSide(Order.Side.BUY)).isEqualTo("buy");
    }

    @Test
    @DisplayName("U6.2 — доменная продажа в словарь площадки")
    void u6_2_domainSellBecomesTheSourceWord() {
        assertThat(converter.orderSide(Order.Side.SELL)).isEqualTo("sell");
    }

    /** Ключ из тела запроса опускает аннотация формы (звено `Z3`). */
    @Test
    @DisplayName("U6.3 — пустая сторона в запрос не уезжает")
    void u6_3_anEmptySideIsEmptiness() {
        assertThat(converter.orderSide(null)).isNull();
    }

    @Test
    @DisplayName("U6.4 — слово площадки в доменную покупку")
    void u6_4_theSourceWordBecomesDomainBuy() {
        assertThat(converter.orderSideToDomain("buy")).isEqualTo(Order.Side.BUY);
    }

    @Test
    @DisplayName("U6.5 — слово площадки в доменную продажу")
    void u6_5_theSourceWordBecomesDomainSell() {
        assertThat(converter.orderSideToDomain("sell")).isEqualTo(Order.Side.SELL);
    }

    /** Поля нет — сторона не обновляется. */
    @Test
    @DisplayName("U6.6 — отсутствие стороны в ответе даёт пустоту")
    void u6_6_anAbsentSideIsEmptiness() {
        assertThat(converter.orderSideToDomain(null)).isNull();
    }

    /** Пустая строка есть присутствующее значение вне словаря. */
    @Test
    @DisplayName("U6.7 — пустая строка стороны отказывает")
    void u6_7_anEmptySideStringRefuses() {
        assertThatThrownBy(() -> converter.orderSideToDomain(""))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    /** Словарь площадки строчный: разбор подбором завёл бы вторую конвенцию. */
    @Test
    @DisplayName("U6.8 — сторона в верхнем регистре отказывает")
    void u6_8_theSideCaseIsNotGuessed() {
        assertThatThrownBy(() -> converter.orderSideToDomain("BUY"))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    /** Слово чужого словаря: у позиции оно законно, у заявки — нет. */
    @Test
    @DisplayName("U6.9 — слово словаря позиции у стороны заявки отказывает")
    void u6_9_aForeignDictionaryWordRefuses() {
        assertThatThrownBy(() -> converter.orderSideToDomain("long"))
                .isInstanceOf(ExternalStatusException.class)
                .extracting(failure -> ((ExternalStatusException) failure).getReasonCode())
                .isEqualTo(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS);
    }

    @Test
    @DisplayName("U6.10 — доменное направление условной заявки в словарь площадки")
    void u6_10_domainAlgoDirectionBecomesTheSourceWord() {
        assertThat(converter.algoSide(AlgoOrder.Direction.BUY)).isEqualTo("buy");
    }

    @Test
    @DisplayName("U6.11 — пустое направление условной заявки даёт пустоту")
    void u6_11_anEmptyAlgoDirectionIsEmptiness() {
        assertThat(converter.algoSide(null)).isNull();
    }

    @Test
    @DisplayName("U6.12 — доменная база триггера в словарь площадки")
    void u6_12_domainTriggerTypeBecomesTheSourceWord() {
        assertThat(converter.triggerType(AlgoOrder.TriggerPriceType.MARK)).isEqualTo("mark");
    }

    /** Не то же, что биржевое умолчание: у встроенной защиты база заполняется всегда. */
    @Test
    @DisplayName("U6.13 — пустая база триггера даёт пустоту")
    void u6_13_anEmptyTriggerTypeIsEmptiness() {
        assertThat(converter.triggerType(null)).isNull();
    }

    @Test
    @DisplayName("U6.14 — эхо базы триггера в доменное значение")
    void u6_14_theTriggerTypeEchoBecomesDomain() {
        assertThat(converter.triggerPriceType("mark")).isEqualTo(AlgoOrder.TriggerPriceType.MARK);
    }

    /** Объявленная асимметрия со стороной заявки: эхо базы сверяется, а не исполняется. */
    @Test
    @DisplayName("U6.15 — регистр эха базы триггера подбирается")
    void u6_15_theTriggerTypeEchoIsCaseInsensitive() {
        assertThat(converter.triggerPriceType("MARK")).isEqualTo(AlgoOrder.TriggerPriceType.MARK);
    }

    /** Сверку базы запускает только распознанное эхо. */
    @Test
    @DisplayName("U6.16 — пустое эхо базы триггера даёт пустоту")
    void u6_16_anEmptyTriggerTypeEchoIsEmptiness() {
        assertThat(converter.triggerPriceType("")).isNull();
    }

    @Test
    @DisplayName("U6.17 — эхо базы вне перечня даёт пустоту, а не отказ")
    void u6_17_anUnknownTriggerTypeEchoIsEmptinessNotRefusal() {
        assertThat(converter.triggerPriceType("oracle")).isNull();
    }

    @Test
    @DisplayName("U6.18 — направление закрытой позиции в доменное значение")
    void u6_18_theCloseDirectionBecomesDomain() {
        assertThat(converter.closeDirection("long")).isEqualTo(Position.Direction.LONG);
    }

    @Test
    @DisplayName("U6.19 — регистр направления закрытой позиции подбирается")
    void u6_19_theCloseDirectionIsCaseInsensitive() {
        assertThat(converter.closeDirection("SHORT")).isEqualTo(Position.Direction.SHORT);
    }

    @Test
    @DisplayName("U6.20 — обрамляющие пробелы направления снимаются")
    void u6_20_theCloseDirectionIsTrimmed() {
        assertThat(converter.closeDirection(" long ")).isEqualTo(Position.Direction.LONG);
    }

    /** Без направления материализация эпизода отказала бы тихо. */
    @Test
    @DisplayName("U6.21 — пустая строка направления закрытия отказывает")
    void u6_21_anEmptyCloseDirectionRefuses() {
        assertThatThrownBy(() -> converter.closeDirection(""))
                .isInstanceOf(ExternalInvariantViolationException.class)
                .hasMessageContaining("эпизод не материализуем");
    }

    /** Объявленная асимметрия со стороной заявки: там пустота законна, здесь нет. */
    @Test
    @DisplayName("U6.22 — отсутствие направления закрытия отказывает так же")
    void u6_22_anAbsentCloseDirectionRefusesTheSameWay() {
        assertThatThrownBy(() -> converter.closeDirection(null))
                .isInstanceOf(ExternalInvariantViolationException.class)
                .hasMessageContaining("эпизод не материализуем");
    }

    /** Перечень нераспознанного собирается по логам: значение стои́т в сообщении. */
    @Test
    @DisplayName("U6.23 — направление вне перечня отказывает, значение в сообщении")
    void u6_23_anUnknownCloseDirectionRefusesWithItsValue() {
        assertThatThrownBy(() -> converter.closeDirection("net"))
                .isInstanceOf(ExternalInvariantViolationException.class)
                .hasMessageContaining("net");
    }

    @Test
    @DisplayName("U6.24 — код успеха даёт истину")
    void u6_24_theSuccessCodeIsTrue() {
        assertThat(converter.ackSuccess("0")).isTrue();
    }

    @Test
    @DisplayName("U6.25 — код отказа даёт ложь")
    void u6_25_aFailureCodeIsFalse() {
        assertThat(converter.ackSuccess("51008")).isFalse();
    }

    /** Направление консервативное: пустой код успехом не считается. */
    @Test
    @DisplayName("U6.26 — пустой код успехом не считается")
    void u6_26_anEmptyCodeIsNotSuccess() {
        assertThat(converter.ackSuccess("")).isFalse();
        assertThat(converter.ackSuccess(null)).isFalse();
    }
}
