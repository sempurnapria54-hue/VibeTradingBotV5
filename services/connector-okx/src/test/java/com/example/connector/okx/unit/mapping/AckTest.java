package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.integration.external.api.model.okx.response.AlgoOrderAckOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.OrderAckOkxResponse;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Подтверждение приёма: два кода на одном ответе — группа `U27`
 * документа `.claude/tests/cases/okx-mapping.md` (javadoc
 * {@code OrderMapper.integrationToAck}; docs/rules/ack-not-runtime-truth.md:
 * подтверждение приёма истиной исполнения не является).
 *
 * <p><b>Базовая сборка:</b> {@code OrderAckOkxResponse} с биржевым и
 * клиентским идентификаторами, кодом успеха, пустым сообщением и
 * временем приёма; код и сообщение верхнего уровня — третьим и
 * четвёртым аргументами.
 *
 * <p><b>Кодов на ответе два, и переход выбирает между ними:</b> на
 * отказе площадка оставляет пер-заявочные код и сообщение пустыми, а
 * причина живёт в коде верхнего уровня, поэтому код подтверждения
 * берётся первым непустым из пары. Признак успеха при этом читается
 * <b>только</b> пер-заявочным кодом, и асимметрия названа, а не
 * умолчана ({@code U27.5}).
 */
class AckTest {

    private final OrderMapper orderMapper = Mappers.order();
    private final AlgoOrderMapper algoOrderMapper = Mappers.algoOrder();

    private static OrderAckOkxResponse ack(String code, String message, String timestamp) {
        OrderAckOkxResponse ack = new OrderAckOkxResponse();
        ack.setOrdId("1");
        ack.setClOrdId("tb-1");
        ack.setsCode(code);
        ack.setsMsg(message);
        ack.setTs(timestamp);
        return ack;
    }

    private static AlgoOrderAckOkxResponse algoAck(String code, String message) {
        AlgoOrderAckOkxResponse ack = new AlgoOrderAckOkxResponse();
        ack.setAlgoId("9");
        ack.setAlgoClOrdId("tb-9");
        ack.setsCode(code);
        ack.setsMsg(message);
        return ack;
    }

    @Test
    @DisplayName("U27.1 — базовая сборка: идентичность, код успеха и момент приёма")
    void u27_1_theBaseAssemblyBuildsTheAck() {
        ExchangeAck built = orderMapper.integrationToAck(
                ack("0", "", OkxFixture.CREATED_MILLIS), "0", "");

        assertThat(built.getExternalId()).isEqualTo("1");
        assertThat(built.getInternalId()).isEqualTo("tb-1");
        assertThat(built.getCode()).isEqualTo("0");
        assertThat(built.getSuccess()).isTrue();
        assertThat(built.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(built.getMessage()).isNull();
    }

    /** Пер-заявочные значения старше верхних. */
    @Test
    @DisplayName("U27.2 — пер-заявочный отказ старше верхнего успеха")
    void u27_2_thePerOrderValuesWinOverTheTopLevel() {
        ExchangeAck built = orderMapper.integrationToAck(
                ack("51008", "Insufficient balance", OkxFixture.CREATED_MILLIS), "0", "");

        assertThat(built.getCode()).isEqualTo("51008");
        assertThat(built.getMessage()).isEqualTo("Insufficient balance");
        assertThat(built.getSuccess()).isFalse();
    }

    /** Запасной путь: подтверждение не несёт пустоты на отказе. */
    @Test
    @DisplayName("U27.3 — пустые пер-заявочные значения: срабатывает верхний уровень")
    void u27_3_theTopLevelFallbackFires() {
        ExchangeAck built = orderMapper.integrationToAck(
                ack("", "", OkxFixture.CREATED_MILLIS), "51008", "Insufficient balance");

        assertThat(built.getCode()).isEqualTo("51008");
        assertThat(built.getMessage()).isEqualTo("Insufficient balance");
        assertThat(built.getSuccess()).isFalse();
    }

    /** Пустая строка и отсутствие значения здесь не разводятся. */
    @Test
    @DisplayName("U27.4 — отсутствие пер-заявочного кода даёт тот же исход, что пустая строка")
    void u27_4_anAbsentPerOrderCodeFallsBackToo() {
        ExchangeAck built = orderMapper.integrationToAck(
                ack(null, null, OkxFixture.CREATED_MILLIS), "51008", "Insufficient balance");

        assertThat(built.getCode()).isEqualTo("51008");
        assertThat(built.getMessage()).isEqualTo("Insufficient balance");
        assertThat(built.getSuccess()).isFalse();
    }

    /**
     * Кейс закрепляет наблюдаемое и назван ограничением: дом признака успеха —
     * пер-заявочный код, и другого ожидания у кейса нет.
     */
    @Test
    @DisplayName("U27.5 — пустой пер-заявочный код при верхнем успехе: код успеха при ложном признаке")
    void u27_5_theAckCanContradictItself() {
        ExchangeAck built = orderMapper.integrationToAck(
                ack("", "", OkxFixture.CREATED_MILLIS), "0", "");

        assertThat(built.getCode()).isEqualTo("0");
        assertThat(built.getSuccess()).isFalse();
    }

    /** Подставленные локальные часы в сравнение войти не могут. */
    @Test
    @DisplayName("U27.6 — пустое время приёма даёт пустоту")
    void u27_6_anEmptyReceiptTimeIsEmptiness() {
        ExchangeAck built = orderMapper.integrationToAck(ack("0", "", ""), "0", "");

        assertThat(built.getExternalCreatedAt()).isNull();
    }

    /** Поля времени у этой формы источника нет вовсе. */
    @Test
    @DisplayName("U27.7 — подтверждение условной заявки: момента приёма нет всегда")
    void u27_7_theAlgoAckNeverCarriesAMoment() {
        ExchangeAck built = algoOrderMapper.integrationToAck(algoAck("0", ""), "0", "");

        assertThat(built.getExternalId()).isEqualTo("9");
        assertThat(built.getInternalId()).isEqualTo("tb-9");
        assertThat(built.getCode()).isEqualTo("0");
        assertThat(built.getSuccess()).isTrue();
        assertThat(built.getExternalCreatedAt()).isNull();
    }

    @Test
    @DisplayName("U27.8 — тот же запасной путь у условной заявки")
    void u27_8_theAlgoAckFallsBackTheSameWay() {
        ExchangeAck built =
                algoOrderMapper.integrationToAck(algoAck("", ""), "51000", "Parameter sz error");

        assertThat(built.getCode()).isEqualTo("51000");
        assertThat(built.getMessage()).isEqualTo("Parameter sz error");
        assertThat(built.getSuccess()).isFalse();
    }

    /** Сущность адресуется клиентским идентификатором. */
    @Test
    @DisplayName("U27.9 — площадка идентификатора не вернула: пустая строка")
    void u27_9_anEmptyExchangeIdIsAnEmptyString() {
        OrderAckOkxResponse source = ack("0", "", OkxFixture.CREATED_MILLIS);
        source.setOrdId("");

        ExchangeAck built = orderMapper.integrationToAck(source, "0", "");

        assertThat(built.getExternalId()).isNotNull().isEmpty();
        assertThat(built.getInternalId()).isEqualTo("tb-1");
    }

    /**
     * Выбор первого непустого стои́т выражением вне охраны (звено `Z1`).
     * Кейс охраны второго рубежа: читатель источника пустую полезную нагрузку
     * до перехода не пускает.
     */
    @Test
    @DisplayName("U27.10 — пустая форма подтверждения при непустых верхних значениях роняет переход")
    void u27_10_anEmptyAckMeetsAnExpressionOutsideTheGuard() {
        assertThatThrownBy(() -> orderMapper.integrationToAck(null, "51008", "Insufficient balance"))
                .isInstanceOf(NullPointerException.class);

        assertThat(orderMapper.integrationToAck(null, null, null)).isNull();
    }
}
