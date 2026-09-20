package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.AlgoOrderOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.BalanceOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderAckOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderBookOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.PositionOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.PositionsHistoryOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.TradeFeeOkxResponse;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Манглинг имени свойства: четырнадцать полей восьми форм — группа `U2`
 * документа `.claude/tests/cases/okx-mapping.md` (javadoc каждой формы,
 * где явное имя свойства поставлено; `.claude/rules/tech-radar.md`,
 * строка Jackson 3).
 *
 * <p><b>Базовая сборка:</b> тело ответа площадки с ключами
 * {@code sCode}/{@code sMsg}/{@code cTime}/{@code uTime} в том
 * написании, в каком их шлёт площадка; разбор — сериализатором провода
 * ({@link WireJson}). Проверяется <b>значение поля</b> разобранной
 * формы, а не факт разбора: имя аксессора у поля, второй символ
 * которого заглавный, выводится процессором и не написано ни в одном
 * файле, поэтому дефект этого класса ловится только прогоном через
 * сериализатор.
 */
class PropertyNameManglingJsonTest {

    @Test
    @DisplayName("U2.1-J — подтверждение приёма заявки: оба поля явного имени непусты")
    void u2_1_orderAckCarriesBothExplicitlyNamedFields() {
        OrderAckOkxResponse ack = WireJson.read(
                "{\"ordId\":\"1\",\"clOrdId\":\"tb-1\",\"sCode\":\"0\",\"sMsg\":\"\",\"ts\":\"1700000000000\"}",
                OrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isEqualTo("0");
        assertThat(ack.getsMsg()).isEmpty();
        assertThat(ack.getOrdId()).isEqualTo("1");
        assertThat(ack.getClOrdId()).isEqualTo("tb-1");
        assertThat(ack.getTs()).isEqualTo("1700000000000");
    }

    /** Имя свойства фиксировано дословно. */
    @Test
    @DisplayName("U2.2-J — ключ написан строчной буквой")
    void u2_2_theKeyCaseIsNotGuessed() {
        OrderAckOkxResponse ack = WireJson.read(
                "{\"ordId\":\"1\",\"clOrdId\":\"tb-1\",\"scode\":\"0\",\"sMsg\":\"\",\"ts\":\"1700000000000\"}",
                OrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isNull();
    }

    @Test
    @DisplayName("U2.3-J — подтверждение приёма условной заявки: времени у формы нет вовсе")
    void u2_3_algoAckHasNoTimestampField() {
        AlgoOrderAckOkxResponse ack = WireJson.read(
                "{\"algoId\":\"9\",\"algoClOrdId\":\"tb-9\",\"sCode\":\"51000\",\"sMsg\":\"Parameter sz error\"}",
                AlgoOrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isEqualTo("51000");
        assertThat(ack.getsMsg()).isEqualTo("Parameter sz error");
        assertThat(AlgoOrderAckOkxResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("ts");
    }

    @Test
    @DisplayName("U2.4-J — времена создания и обновления заявки непусты и различны")
    void u2_4_orderTimesAreBound() {
        OrderOkxResponse response = WireJson.read(
                "{\"cTime\":\"1700000000000\",\"uTime\":\"1700000060000\"}", OrderOkxResponse.class);

        assertThat(response.getcTime()).isEqualTo("1700000000000");
        assertThat(response.getuTime()).isEqualTo("1700000060000");
        assertThat(response.getcTime()).isNotEqualTo(response.getuTime());
    }

    @Test
    @DisplayName("U2.5-J — те же ключи у условной заявки")
    void u2_5_algoOrderTimesAreBound() {
        AlgoOrderOkxResponse response = WireJson.read(
                "{\"cTime\":\"1700000000000\",\"uTime\":\"1700000060000\"}", AlgoOrderOkxResponse.class);

        assertThat(response.getcTime()).isEqualTo("1700000000000");
        assertThat(response.getuTime()).isEqualTo("1700000060000");
    }

    @Test
    @DisplayName("U2.6-J — те же ключи у живой позиции")
    void u2_6_positionTimesAreBound() {
        PositionOkxResponse response = WireJson.read(
                "{\"cTime\":\"1700000000000\",\"uTime\":\"1700000060000\"}", PositionOkxResponse.class);

        assertThat(response.getcTime()).isEqualTo("1700000000000");
        assertThat(response.getuTime()).isEqualTo("1700000060000");
    }

    /** Время обновления — ось окна и пагинации: пустота здесь обнулила бы обход. */
    @Test
    @DisplayName("U2.7-J — те же ключи у записи истории позиций")
    void u2_7_positionHistoryTimesAreBound() {
        PositionsHistoryOkxResponse response = WireJson.read(
                "{\"cTime\":\"1700000000000\",\"uTime\":\"1700000060000\"}", PositionsHistoryOkxResponse.class);

        assertThat(response.getcTime()).isEqualTo("1700000000000");
        assertThat(response.getuTime()).isEqualTo("1700000060000");
    }

    /** Два разных уровня, две разные формы — явное имя стои́т на каждой. */
    @Test
    @DisplayName("U2.8-J — время обновления непусто на обоих уровнях баланса")
    void u2_8_balanceTimeIsBoundOnBothLevels() {
        BalanceOkxResponse response = WireJson.read(
                "{\"uTime\":\"1700000000000\",\"totalEq\":\"1000\",\"adjEq\":\"1000\",\"availEq\":\"900\","
                        + "\"details\":[{\"ccy\":\"USDT\",\"uTime\":\"1700000000000\",\"eq\":\"900\"}]}",
                BalanceOkxResponse.class);

        assertThat(response.getuTime()).isEqualTo("1700000000000");
        assertThat(response.getDetails()).hasSize(1);
        assertThat(response.getDetails().getFirst().getuTime()).isEqualTo("1700000000000");
        assertThat(response.getDetails().getFirst().getCcy()).isEqualTo("USDT");
    }

    /** Форма источника держит числа строками по построению. */
    @Test
    @DisplayName("U2.9-J — число провода ложится в строковое поле")
    void u2_9_aNumberOnTheWireLandsInAStringField() {
        OrderOkxResponse response = WireJson.read("{\"cTime\":1700000000000}", OrderOkxResponse.class);

        assertThat(response.getcTime()).isEqualTo("1700000000000");
    }

    /** Различие несущее: на нём стои́т запасной путь подтверждения приёма. */
    @Test
    @DisplayName("U2.10-J — пустая строка кода приёма остаётся пустой строкой")
    void u2_10_anEmptyStringIsNotEmptiness() {
        OrderAckOkxResponse ack = WireJson.read("{\"ordId\":\"1\",\"sCode\":\"\"}", OrderAckOkxResponse.class);

        assertThat(ack.getsCode()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U2.11-J — встроенная защита в теле родителя разобрана всеми полями")
    void u2_11_attachedProtectionInParentBodyIsParsed() {
        OrderOkxResponse response = WireJson.read(
                "{\"attachAlgoOrds\":[{\"attachAlgoId\":\"a1\",\"attachAlgoClOrdId\":\"tb-p1\","
                        + "\"slTriggerPx\":\"100\",\"slTriggerPxType\":\"mark\","
                        + "\"failCode\":\"\",\"failReason\":\"\"}]}",
                OrderOkxResponse.class);

        assertThat(response.getAttachAlgoOrds()).hasSize(1);
        assertThat(response.getAttachAlgoOrds().getFirst().getAttachAlgoId()).isEqualTo("a1");
        assertThat(response.getAttachAlgoOrds().getFirst().getAttachAlgoClOrdId()).isEqualTo("tb-p1");
        assertThat(response.getAttachAlgoOrds().getFirst().getSlTriggerPx()).isEqualTo("100");
        assertThat(response.getAttachAlgoOrds().getFirst().getSlTriggerPxType()).isEqualTo("mark");
        assertThat(response.getAttachAlgoOrds().getFirst().getFailCode()).isEmpty();
        assertThat(response.getAttachAlgoOrds().getFirst().getFailReason()).isEmpty();
    }

    /** Ставки — сырые строки со знаком источника. */
    @Test
    @DisplayName("U2.12-J — вложенная форма группы ставок разобрана")
    void u2_12_feeGroupIsParsed() {
        TradeFeeOkxResponse response = WireJson.read(
                "{\"instType\":\"SWAP\",\"level\":\"Lv1\",\"ts\":\"1700000000000\","
                        + "\"feeGroup\":[{\"groupId\":\"1\",\"taker\":\"-0.0005\",\"maker\":\"-0.0002\"}]}",
                TradeFeeOkxResponse.class);

        assertThat(response.getInstType()).isEqualTo("SWAP");
        assertThat(response.getLevel()).isEqualTo("Lv1");
        assertThat(response.getTs()).isEqualTo("1700000000000");
        assertThat(response.getFeeGroup()).hasSize(1);
        assertThat(response.getFeeGroup().getFirst().getGroupId()).isEqualTo("1");
        assertThat(response.getFeeGroup().getFirst().getTaker()).isEqualTo("-0.0005");
        assertThat(response.getFeeGroup().getFirst().getMaker()).isEqualTo("-0.0002");
    }

    @Test
    @DisplayName("U2.13-J — список связанных заявок разобран в том же порядке")
    void u2_13_linkedOrderIdsKeepTheirOrder() {
        AlgoOrderOkxResponse response =
                WireJson.read("{\"ordIdList\":[\"1\",\"2\"]}", AlgoOrderOkxResponse.class);

        assertThat(response.getOrdIdList()).containsExactly("1", "2");
    }

    @Test
    @DisplayName("U2.14-J — двумерный список уровней книги разобран")
    void u2_14_orderBookLevelsAreParsed() {
        OrderBookOkxResponse response = WireJson.read(
                "{\"asks\":[[\"100.1\",\"5\",\"0\",\"3\"]],\"bids\":[[\"100.0\",\"7\",\"0\",\"2\"]],"
                        + "\"ts\":\"1700000000000\"}",
                OrderBookOkxResponse.class);

        assertThat(response.getTs()).isEqualTo("1700000000000");
        assertThat(response.getAsks()).hasSize(1);
        assertThat(response.getAsks().getFirst()).containsExactly("100.1", "5", "0", "3");
        assertThat(response.getBids()).hasSize(1);
        assertThat(response.getBids().getFirst()).containsExactly("100.0", "7", "0", "2");
    }
}
