package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.OkxApiResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.TickerOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.TradeFeeOkxResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Конверт ответа площадки: код, сообщение, полезная нагрузка — группа
 * `U1` документа `.claude/tests/cases/okx-mapping.md`
 * (docs/rules/raw-exchange-dto-boundary.md; javadoc
 * {@code OkxApiResponse}).
 *
 * <p><b>Базовая сборка:</b> тело ответа площадки строкой JSON;
 * разбирается сериализатором провода ({@link WireJson}) в
 * {@code OkxApiResponse<T>} с явно заданным типом элемента.
 */
class ApiEnvelopeJsonTest {

    @Test
    @DisplayName("U1.1-J — успешный конверт с одним элементом полезной нагрузки")
    void u1_1_successEnvelopeCarriesOneParsedElement() {
        OkxApiResponse<OrderOkxResponse> envelope = WireJson.envelope(
                "{\"code\":\"0\",\"msg\":\"\",\"data\":[{\"ordId\":\"1\",\"clOrdId\":\"tb-1\",\"state\":\"live\"}]}",
                OrderOkxResponse.class);

        assertThat(envelope.getCode()).isEqualTo("0");
        assertThat(envelope.getMsg()).isEmpty();
        assertThat(envelope.getData()).hasSize(1);
        assertThat(envelope.getData().getFirst().getOrdId()).isEqualTo("1");
        assertThat(envelope.getData().getFirst().getClOrdId()).isEqualTo("tb-1");
        assertThat(envelope.getData().getFirst().getState()).isEqualTo("live");
    }

    /** Ошибочный конверт несёт пустой список, а не пустоту. */
    @Test
    @DisplayName("U1.2-J — ошибочный код и текст площадки дословно")
    void u1_2_failureEnvelopeCarriesEmptyPayload() {
        OkxApiResponse<OrderOkxResponse> envelope = WireJson.envelope(
                "{\"code\":\"51008\",\"msg\":\"Order placement failed due to insufficient balance\",\"data\":[]}",
                OrderOkxResponse.class);

        assertThat(envelope.getCode()).isEqualTo("51008");
        assertThat(envelope.getMsg()).isEqualTo("Order placement failed due to insufficient balance");
        assertThat(envelope.getData()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U1.3-J — три элемента в порядке ответа")
    void u1_3_orderOfElementsIsPreserved() {
        OkxApiResponse<TradeFeeOkxResponse> envelope = WireJson.envelope(
                "{\"code\":\"0\",\"msg\":\"\",\"data\":[{\"level\":\"Lv1\"},{\"level\":\"Lv2\"},{\"level\":\"Lv3\"}]}",
                TradeFeeOkxResponse.class);

        assertThat(envelope.getData()).extracting(TradeFeeOkxResponse::getLevel)
                .containsExactly("Lv1", "Lv2", "Lv3");
    }

    /** Отсутствие ключа и пустой массив — разные входы. */
    @Test
    @DisplayName("U1.4-J — тело без ключа полезной нагрузки")
    void u1_4_missingPayloadKeyIsEmptinessNotEmptyList() {
        OkxApiResponse<OrderOkxResponse> envelope =
                WireJson.envelope("{\"code\":\"0\",\"msg\":\"\"}", OrderOkxResponse.class);

        assertThat(envelope.getCode()).isEqualTo("0");
        assertThat(envelope.getData()).isNull();
    }

    @Test
    @DisplayName("U1.5-J — неизвестный ключ верхнего уровня разбор не роняет")
    void u1_5_unknownTopLevelKeyIsTolerated() {
        OkxApiResponse<OrderOkxResponse> envelope = WireJson.envelope(
                "{\"code\":\"0\",\"data\":[],\"inTime\":\"1700000000\"}", OrderOkxResponse.class);

        assertThat(envelope.getCode()).isEqualTo("0");
        assertThat(envelope.getData()).isEmpty();
    }

    @Test
    @DisplayName("U1.6-J — неизвестный ключ элемента игнорируется, известные разобраны")
    void u1_6_unknownElementKeyIsIgnored() {
        OkxApiResponse<TickerOkxResponse> envelope = WireJson.envelope(
                "{\"code\":\"0\",\"data\":[{\"instId\":\"ETH-USDT-SWAP\",\"inTime\":\"1700000000\"}]}",
                TickerOkxResponse.class);

        assertThat(envelope.getData()).hasSize(1);
        assertThat(envelope.getData().getFirst().getInstId()).isEqualTo("ETH-USDT-SWAP");
    }

    /** Обёртка одна и та же у объектных и позиционных элементов. */
    @Test
    @DisplayName("U1.7-J — позиционный элемент полезной нагрузки")
    void u1_7_positionalElementIsAListOfStrings() {
        OkxApiResponse<List<String>> envelope = WireJson.positionalEnvelope(
                "{\"code\":\"0\",\"msg\":\"\","
                        + "\"data\":[[\"1700000000000\",\"1\",\"2\",\"0\",\"3\",\"4\",\"5\",\"6\",\"1\"]]}");

        assertThat(envelope.getData()).hasSize(1);
        assertThat(envelope.getData().getFirst()).hasSize(9)
                .containsExactly("1700000000000", "1", "2", "0", "3", "4", "5", "6", "1");
    }
}
