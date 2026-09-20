package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.integration.external.api.model.okx.response.CandleOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OkxApiResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Позиционный массив свечи: разбор и его охрана — группа `U3`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Candle.md §«Формат свечи»: порядок строго
 * фиксирован, длина строго девять, и длину валидирует граница).
 *
 * <p><b>Базовая сборка:</b> массив из девяти строк
 * {@code [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]},
 * поданный в фабрику {@code CandleOkxResponse.of}.
 */
class CandlePositionalArrayTest {

    private static final List<String> NINE =
            List.of("1700000000000", "100", "110", "90", "105", "12", "13", "14", "1");

    @Test
    @DisplayName("U3.1 — девять непустых строк ложатся в девять именованных полей")
    void u3_1_ninePositionsBecomeNineNamedFields() {
        CandleOkxResponse candle = CandleOkxResponse.of(NINE);

        assertThat(candle.getTs()).isEqualTo("1700000000000");
        assertThat(candle.getOpen()).isEqualTo("100");
        assertThat(candle.getHigh()).isEqualTo("110");
        assertThat(candle.getLow()).isEqualTo("90");
        assertThat(candle.getClose()).isEqualTo("105");
        assertThat(candle.getVolume()).isEqualTo("12");
        assertThat(candle.getVolumeCurrency()).isEqualTo("13");
        assertThat(candle.getVolumeCurrencyQuote()).isEqualTo("14");
        assertThat(candle.getConfirm()).isEqualTo("1");
    }

    @Test
    @DisplayName("U3.2 — восемь строк: формы не создаётся")
    void u3_2_aShorterArrayIsRefused() {
        List<String> eight = NINE.subList(0, 8);

        assertThatThrownBy(() -> CandleOkxResponse.of(eight))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9")
                .hasMessageContaining("8");
    }

    /** Длина строго девять: лишний элемент не отбрасывается. */
    @Test
    @DisplayName("U3.3 — десять строк: лишний элемент не отбрасывается")
    void u3_3_aLongerArrayIsRefused() {
        List<String> ten = new java.util.ArrayList<>(NINE);
        ten.add("extra");

        assertThatThrownBy(() -> CandleOkxResponse.of(ten))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    @DisplayName("U3.4 — пустой список: в сообщении ноль элементов")
    void u3_4_anEmptyListIsRefusedWithZero() {
        assertThatThrownBy(() -> CandleOkxResponse.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("got: 0");
    }

    /** Пустой и отсутствующий вход не разводятся: оба означают «формы источника нет». */
    @Test
    @DisplayName("U3.5 — пустота вместо списка: тот же отказ тем же сообщением")
    void u3_5_emptinessIsRefusedTheSameWay() {
        assertThatThrownBy(() -> CandleOkxResponse.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("got: 0");
    }

    /** Фильтрация незакрытых свечей — не здесь, а на маппинге. */
    @Test
    @DisplayName("U3.6 — признак незакрытой свечи форму не отвергает")
    void u3_6_anUnconfirmedCandleIsStillAForm() {
        List<String> unconfirmed =
                List.of("1700000000000", "100", "110", "90", "105", "12", "13", "14", "0");

        assertThat(CandleOkxResponse.of(unconfirmed).getConfirm()).isEqualTo("0");
    }

    @Test
    @DisplayName("U3.7 — оба объёма разобраны в форму источника и за границу не выходят")
    void u3_7_validationOnlyVolumesStayInTheSourceForm() {
        CandleOkxResponse candle = CandleOkxResponse.of(NINE);

        assertThat(candle.getVolumeCurrency()).isEqualTo("13");
        assertThat(candle.getVolumeCurrencyQuote()).isEqualTo("14");
        assertThat(com.example.connector.okx.snapshot.CandleExternalSnapshot.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("volumeCurrency", "volumeCurrencyQuote");
    }

    /** Площадка отдаёт от новых к старым: перестановка сломала бы окно. */
    @Test
    @DisplayName("U3.8-J — порядок позиционных элементов полезной нагрузки сохранён")
    void u3_8_theOrderOfPositionalElementsIsPreserved() {
        OkxApiResponse<List<String>> envelope = WireJson.positionalEnvelope(
                "{\"code\":\"0\",\"data\":["
                        + "[\"1700000060000\",\"2\",\"2\",\"2\",\"2\",\"2\",\"2\",\"2\",\"1\"],"
                        + "[\"1700000000000\",\"1\",\"1\",\"1\",\"1\",\"1\",\"1\",\"1\",\"1\"]]}");

        assertThat(envelope.getData()).hasSize(2);
        assertThat(envelope.getData().getFirst().getFirst()).isEqualTo("1700000060000");
        assertThat(envelope.getData().getLast().getFirst()).isEqualTo("1700000000000");
    }

    /** Фабрика длину мерит, а не содержимое: пустота приземляется на маппинге. */
    @Test
    @DisplayName("U3.9 — пустая строка на позиции времени открытия форму не отвергает")
    void u3_9_anEmptyPositionIsStillALength() {
        List<String> blankTs = List.of("", "100", "110", "90", "105", "12", "13", "14", "1");

        assertThat(CandleOkxResponse.of(blankTs).getTs()).isEmpty();
    }
}
