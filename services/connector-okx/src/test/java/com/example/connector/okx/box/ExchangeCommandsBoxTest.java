package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Команды площадке и семантика подтверждения — группа {@code B2}
 * документа `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Выход группы — ЗАПРОС, а не ответ.</b> Команда возвращает наружу
 * подтверждение приёма, и оно мало: истина о том, что произошло, узнаётся
 * наблюдением ({@code docs/rules/ack-not-runtime-truth.md}). Проверяемое
 * здесь — что ушло на площадку: путь, тело, константы интеграции, выбор
 * семьи.
 *
 * <p><b>Реджект площадки и отказ границы разведены, и различие
 * счётно:</b> на реджекте подтверждение приезжает наружу со своим
 * {@code success=false} — это бизнес-исход; на пустом {@code data}
 * подтверждения приёма не было вовсе, и наружу уезжает отказ класса
 * границы. Слей их — и ядро приняло бы непоставленную заявку за
 * отвергнутую.
 */
class ExchangeCommandsBoxTest extends SharedConnectorBox {

    private static final String ORDERS = "/orders?externalInstrumentId=" + INSTRUMENT;

    private static final String ORDER_CANCELLATIONS = "/orders/cancellations?externalInstrumentId=" + INSTRUMENT;

    private static final String ALGO_ORDERS = "/algo-orders?externalInstrumentId=" + INSTRUMENT;

    private static final String ALGO_CANCELLATIONS =
            "/algo-orders/cancellations?externalInstrumentId=" + INSTRUMENT;

    private static final String ATTACHED_CANCELLATIONS =
            "/attached-protections/cancellations?externalInstrumentId=" + INSTRUMENT;

    private static final String AMEND_ORDER_PATH = "/api/v5/trade/amend-order";

    private static final String AMEND_ALGOS_PATH = "/api/v5/trade/amend-algos";

    @Test
    @DisplayName("B2.1 — штатное выставление заявки")
    void b2_1_aRegularOrderPlacement() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH,
                Okx.ok(Okx.acceptedAck("ord-1", Bodies.ORDER_INTERNAL_ID)));

        Answer answer = post(account(ORDERS), Bodies.limitOrder());

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> ack = answer.asObject();
        assertThat(ack.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(ack.get("externalId")).isEqualTo("ord-1");
        assertThat(ack.get("internalId")).isEqualTo(Bodies.ORDER_INTERNAL_ID);
        assertThat(ack.get("externalCreatedAt")).isNotNull();
        assertThat(ack).doesNotContainKeys("data", "msg");

        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDER_PATH);
        assertThat(sent.getMethod().getName()).isEqualTo("POST");
        assertThat(sent.getBodyAsString()).contains("\"instId\":\"" + INSTRUMENT + "\"",
                "\"tdMode\":\"isolated\"", "\"posSide\":\"net\"", "\"tag\":\"tb\"",
                "\"ordType\":\"limit\"", "\"clOrdId\":\"" + Bodies.ORDER_INTERNAL_ID + "\"",
                "\"side\":\"buy\"", "\"sz\":\"1\"", "\"px\":\"50000\"");
        assertThat(exchange.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("B2.2 — бизнес-реджект площадки — это ответ, а не отказ")
    void b2_2_aBusinessRejectIsAnAnswerNotAFailure() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH, Okx.envelope("1", "operation failed",
                Okx.record("ordId", "", "clOrdId", Bodies.ORDER_INTERNAL_ID,
                        "sCode", "51008", "sMsg", "Order placement failed due to insufficient balance",
                        "ts", "1758240000000").text()));

        Answer answer = post(account(ORDERS), Bodies.limitOrder());

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> ack = answer.asObject();
        assertThat(ack.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(ack.get("code")).isEqualTo("51008");
        assertThat(ack.get("message")).isEqualTo("Order placement failed due to insufficient balance");
    }

    @Test
    @DisplayName("B2.3 — пустой `data` на команде — отказ границы с реальными кодом и сообщением")
    void b2_3_anEmptyDataOnACommandIsABoundaryFailure() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH, Okx.failure("50011", "Rate limit reached"));

        Answer answer = post(account(ORDERS), Bodies.limitOrder());

        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_ERROR");
        assertThat(String.valueOf(answer.asObject().get("message")))
                .contains("50011", "Rate limit reached");
        assertThat(answer.asObject()).doesNotContainKey("success");
    }

    @Test
    @DisplayName("B2.4 — реджект без per-order кода падает на код конверта")
    void b2_4_aRejectWithoutPerOrderCodeFallsBackToTheEnvelopeCode() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH, Okx.envelope("1", "Operation failed.",
                Okx.record("ordId", "", "clOrdId", Bodies.ORDER_INTERNAL_ID,
                        "sCode", "", "sMsg", "", "ts", "1758240000000").text()));

        Answer answer = post(account(ORDERS), Bodies.limitOrder());

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> ack = answer.asObject();
        assertThat(ack.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(ack.get("code")).isEqualTo("1");
        assertThat(ack.get("message")).isEqualTo("Operation failed.");
    }

    @Test
    @DisplayName("B2.5 — снятие заявки адресуется обоими идентификаторами")
    void b2_5_anOrderCancellationCarriesBothIdentifiers() {
        exchange.answers(OkxConstants.TRADE_CANCEL_ORDER_PATH,
                Okx.ok(Okx.acceptedAck(Bodies.ORDER_EXTERNAL_ID, Bodies.ORDER_INTERNAL_ID)));

        Answer answer = post(account(ORDER_CANCELLATIONS), Bodies.orderToCancel());

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("success")).isEqualTo(Boolean.TRUE);
        assertThat(exchange.single(OkxConstants.TRADE_CANCEL_ORDER_PATH).getBodyAsString())
                .contains("\"instId\":\"" + INSTRUMENT + "\"",
                        "\"ordId\":\"" + Bodies.ORDER_EXTERNAL_ID + "\"",
                        "\"clOrdId\":\"" + Bodies.ORDER_INTERNAL_ID + "\"");
        assertThat(exchange.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("B2.6 — семья условной заявки выбирает путь снятия")
    void b2_6_theAlgoFamilyChoosesTheCancellationPath() {
        exchange.answers(OkxConstants.TRADE_CANCEL_ALGOS_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CANCEL_ADVANCE_ALGOS_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));

        assertThat(post(account(ALGO_CANCELLATIONS), Bodies.algoOrderToCancel("STOP_LOSS")).status())
                .isEqualTo(200);
        assertThat(post(account(ALGO_CANCELLATIONS), Bodies.algoOrderToCancel("TRAILING_PERCENTS")).status())
                .isEqualTo(200);

        LoggedRequest ordinary = exchange.single(OkxConstants.TRADE_CANCEL_ALGOS_PATH);
        LoggedRequest advance = exchange.single(OkxConstants.TRADE_CANCEL_ADVANCE_ALGOS_PATH);
        assertThat(ordinary.getBodyAsString()).startsWith("[").contains(
                "\"instId\":\"" + INSTRUMENT + "\"", "\"algoId\":\"" + Bodies.ALGO_EXTERNAL_ID + "\"");
        assertThat(advance.getBodyAsString()).startsWith("[").contains(
                "\"instId\":\"" + INSTRUMENT + "\"", "\"algoId\":\"" + Bodies.ALGO_EXTERNAL_ID + "\"");
        assertThat(exchange.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("B2.7 — снятие встроенной защиты всегда идёт обычной семьёй")
    void b2_7_anAttachedProtectionIsAlwaysCancelledByTheOrdinaryFamily() {
        exchange.answers(OkxConstants.TRADE_CANCEL_ALGOS_PATH,
                Okx.ok(Okx.acceptedAlgoAck("algo-att-1", "vtb-att-1")));

        Answer answer = post(account(ATTACHED_CANCELLATIONS), Bodies.attachedProtectionToCancel());

        assertThat(answer.status()).isEqualTo(200);
        assertThat(exchange.single(OkxConstants.TRADE_CANCEL_ALGOS_PATH).getBodyAsString())
                .contains("\"algoId\":\"algo-att-1\"", "\"algoClOrdId\":\"vtb-att-1\"");
        assertThat(exchange.requests(OkxConstants.TRADE_CANCEL_ADVANCE_ALGOS_PATH)).isEmpty();
    }

    @Test
    @DisplayName("B2.8 — закрытие позиции по рынку несёт константы интеграции")
    void b2_8_aMarketPositionClosureCarriesTheIntegrationConstants() {
        exchange.answers(OkxConstants.TRADE_CLOSE_POSITION_PATH,
                Okx.ok(Okx.acceptedAck("ord-close-1", "")));

        Answer answer = post(account("/positions/closures?externalInstrumentId=" + INSTRUMENT
                + "&settleCurrency=USDT"), "");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(exchange.single(OkxConstants.TRADE_CLOSE_POSITION_PATH).getBodyAsString())
                .contains("\"instId\":\"" + INSTRUMENT + "\"", "\"mgnMode\":\"isolated\"",
                        "\"posSide\":\"net\"", "\"ccy\":\"USDT\"", "\"autoCxl\":true");
        assertThat(answer.body()).doesNotContain("mgnMode", "autoCxl", "posSide");
    }

    @Test
    @DisplayName("B2.9 — установка плеча подтверждается кодом конверта")
    void b2_9_theLeverageSettingIsConfirmedByTheEnvelopeCode() {
        exchange.answers(OkxConstants.ACCOUNT_SET_LEVERAGE_PATH, Okx.ok(Okx.record(
                "instId", INSTRUMENT, "lever", "5", "mgnMode", "isolated", "posSide", "net").text()));

        Answer accepted = post(account("/leverage?externalInstrumentId=" + INSTRUMENT + "&leverage=5"), "");

        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.asObject().get("success")).isEqualTo(Boolean.TRUE);
        assertThat(exchange.single(OkxConstants.ACCOUNT_SET_LEVERAGE_PATH).getBodyAsString())
                .contains("\"instId\":\"" + INSTRUMENT + "\"", "\"lever\":\"5\"",
                        "\"mgnMode\":\"isolated\"", "\"posSide\":\"net\"");

        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_SET_LEVERAGE_PATH, Okx.failure("51000", "Parameter lever error"));

        Answer refused = post(account("/leverage?externalInstrumentId=" + INSTRUMENT + "&leverage=5"), "");

        assertThat(refused.errorCode()).isEqualTo("EXCHANGE_ERROR");
    }

    @Test
    @DisplayName("B2.10 — замещения заявки не существует ни на одной тропе")
    void b2_10_thereIsNoAmendmentOnAnyTrope() {
        answerEveryCommand();

        post(account(ORDERS), Bodies.limitOrder());
        post(account(ORDER_CANCELLATIONS), Bodies.orderToCancel());
        post(account(ALGO_ORDERS), Bodies.algoOrderToPlace("STOP_LOSS"));
        post(account(ALGO_CANCELLATIONS), Bodies.algoOrderToCancel("STOP_LOSS"));
        post(account(ATTACHED_CANCELLATIONS), Bodies.attachedProtectionToCancel());
        post(account("/positions/closures?externalInstrumentId=" + INSTRUMENT + "&settleCurrency=USDT"), "");
        post(account("/leverage?externalInstrumentId=" + INSTRUMENT + "&leverage=5"), "");

        assertThat(exchange.requests(AMEND_ORDER_PATH)).isEmpty();
        assertThat(exchange.requests(AMEND_ALGOS_PATH)).isEmpty();
        assertThat(post(account("/orders/replacements?externalInstrumentId=" + INSTRUMENT),
                Bodies.limitOrder()).status()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("B2.11 — род заявки выводится из наличия цены")
    void b2_11_theOrderKindIsDerivedFromThePresenceOfAPrice() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH,
                Okx.ok(Okx.acceptedAck("ord-1", Bodies.ORDER_INTERNAL_ID)));

        post(account(ORDERS), Bodies.limitOrder());
        post(account(ORDERS), Bodies.marketOrder());

        String limit = exchange.requests(OkxConstants.TRADE_ORDER_PATH).getFirst().getBodyAsString();
        String market = exchange.requests(OkxConstants.TRADE_ORDER_PATH).getLast().getBodyAsString();
        assertThat(limit).contains("\"ordType\":\"limit\"", "\"px\":\"50000\"");
        assertThat(market).contains("\"ordType\":\"market\"").doesNotContain("\"px\"");
    }

    /**
     * Намерение уезжает полем запроса, и этим клетка исчерпана: посылочной
     * сверки «отправленное против подтверждённого» дом не объявляет —
     * последствие неисполненного намерения ловит сверка экспозиции ядра
     * ({@code docs/integrations/okx/rules/reduce-only-invariant.md}).
     */
    @Test
    @DisplayName("B2.12 — намерение «только сокращать позицию» уезжает полем запроса")
    void b2_12_theReduceOnlyIntentTravels() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH,
                Okx.ok(Okx.acceptedAck("ord-1", Bodies.ORDER_INTERNAL_ID)));

        Answer placed = post(account(ORDERS), Bodies.reducingOnlyOrder());

        assertThat(placed.status()).isEqualTo(200);
        assertThat(exchange.single(OkxConstants.TRADE_ORDER_PATH).getBodyAsString())
                .contains("\"reduceOnly\":true");
    }

    @Test
    @DisplayName("B2.13 — постановка условной заявки выбирает путь и семью по роду условия")
    void b2_13_anAlgoPlacementChoosesPathAndFamilyByConditionKind() {
        exchange.answers(OkxConstants.TRADE_ORDER_ALGO_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));

        Answer answer = post(account(ALGO_ORDERS), Bodies.algoOrderToPlace("STOP_LOSS"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("success")).isEqualTo(Boolean.TRUE);
        assertThat(answer.asObject().get("externalId")).isEqualTo(Bodies.ALGO_EXTERNAL_ID);
        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDER_ALGO_PATH);
        assertThat(sent.getBodyAsString()).contains("\"instId\":\"" + INSTRUMENT + "\"",
                "\"tdMode\":\"isolated\"", "\"posSide\":\"net\"", "\"ordType\":\"conditional\"",
                "\"algoClOrdId\":\"" + Bodies.ALGO_INTERNAL_ID + "\"", "\"side\":\"sell\"",
                "\"slTriggerPx\":\"49000\"", "\"slTriggerPxType\":\"mark\"", "\"slOrdPx\":\"-1\"");
        assertThat(exchange.count()).isEqualTo(1);

        exchange.reset();
        exchange.answers(OkxConstants.TRADE_ORDER_ALGO_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));

        post(account(ALGO_ORDERS), Bodies.algoOrderToPlace("TRAILING_PERCENTS"));

        assertThat(exchange.single(OkxConstants.TRADE_ORDER_ALGO_PATH).getBodyAsString())
                .contains("\"ordType\":\"move_order_stop\"");
    }

    @Test
    @DisplayName("B2.14 — заявка со встроенной защитой уезжает одним запросом")
    void b2_14_anOrderWithAttachedProtectionTravelsInOneRequest() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH,
                Okx.ok(Okx.acceptedAck("ord-1", Bodies.ORDER_INTERNAL_ID)));

        Answer answer = post(account(ORDERS), Bodies.orderWithAttachedProtection());

        assertThat(answer.status()).isEqualTo(200);
        LoggedRequest sent = exchange.single(OkxConstants.TRADE_ORDER_PATH);
        assertThat(sent.getBodyAsString()).contains("\"attachAlgoOrds\":[",
                "\"attachAlgoClOrdId\":\"vtb-att-1\"", "\"slTriggerPx\":\"49000\"",
                "\"slTriggerPxType\":\"mark\"", "\"slOrdPx\":\"-1\"");
        assertThat(sent.getBodyAsString()).doesNotContain("\"sz\":\"\"");
        assertThat(exchange.requests(OkxConstants.TRADE_ORDER_ALGO_PATH)).isEmpty();
        assertThat(exchange.count()).isEqualTo(1);
    }

    /** Штатное подтверждение на каждом из семи путей команд. */
    private void answerEveryCommand() {
        exchange.answers(OkxConstants.TRADE_ORDER_PATH,
                Okx.ok(Okx.acceptedAck("ord-1", Bodies.ORDER_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CANCEL_ORDER_PATH,
                Okx.ok(Okx.acceptedAck(Bodies.ORDER_EXTERNAL_ID, Bodies.ORDER_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_ORDER_ALGO_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CANCEL_ALGOS_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CANCEL_ADVANCE_ALGOS_PATH,
                Okx.ok(Okx.acceptedAlgoAck(Bodies.ALGO_EXTERNAL_ID, Bodies.ALGO_INTERNAL_ID)));
        exchange.answers(OkxConstants.TRADE_CLOSE_POSITION_PATH,
                Okx.ok(Okx.acceptedAck("ord-close-1", "")));
        exchange.answers(OkxConstants.ACCOUNT_SET_LEVERAGE_PATH, Okx.ok(Okx.record(
                "instId", INSTRUMENT, "lever", "5", "mgnMode", "isolated", "posSide", "net").text()));
    }
}
