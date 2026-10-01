package com.example.connector.okx.box;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Позиции, средства, ставки комиссии — группа {@code B5} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Баланс — объявленное исключение из контракта чтения.</b> У
 * прочих операций пустой ответ означает «не найдено»; у баланса пустота
 * есть отказ: счёт существует всегда, и пустой контейнер означал бы, что
 * ответ не добыт ({@code docs/components/IntegrationService.md}
 * §«Контракт чтения»).
 *
 * <p><b>Обход движений средств идёт НАЗАД до пустой страницы</b>, и
 * курсор границу не пересекает: наружу уезжает склейка страниц, а якорь
 * пагинации остаётся внутри — иначе читатель обязан был бы знать форму
 * пагинации источника.
 */
class PositionsFundsFeesBoxTest extends SharedConnectorBox {

    private static final String OTHER_INSTRUMENT = "ETH-USDT-SWAP";

    private static final String WINDOW_BEGIN = "2026-09-01T00:00:00Z";

    private static final String WINDOW_BEGIN_MILLIS = "1788220800000";

    @Test
    @DisplayName("B5.1 — позиция инструмента и все позиции счёта")
    void b5_1_anInstrumentPositionAndAllAccountPositions() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok(
                Okx.position(INSTRUMENT, "3").text(),
                Okx.position(OTHER_INSTRUMENT, "-2").text()));

        Answer single = get(account("/positions/instrument?externalInstrumentId=" + INSTRUMENT));
        Answer all = get(account("/positions"));

        assertThat(single.status()).isEqualTo(200);
        assertThat(single.asObject().get("status")).isNull();
        assertThat(all.asList()).hasSize(2);
        all.asList().forEach(position ->
                assertThat(position.get("externalInstrumentId")).isIn(INSTRUMENT, OTHER_INSTRUMENT));

        List<LoggedRequest> sent = exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH);
        assertThat(sent.getFirst().getUrl()).contains("instId=" + INSTRUMENT);
        assertThat(sent.getLast().getUrl()).contains("instType=SWAP").doesNotContain("instId=");

        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        assertThat(get(account("/positions")).asList()).isEmpty();
    }

    @Test
    @DisplayName("B5.2 — закрытые эпизоды читаются окном снизу")
    void b5_2_closedEpisodesAreReadByALowerBoundedWindow() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH,
                Okx.ok(Okx.closedPosition(INSTRUMENT).text()));

        Answer answer = get(account("/positions/closed?externalInstrumentId=" + INSTRUMENT
                + "&windowBegin=" + WINDOW_BEGIN));

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> position = answer.single();
        assertThat(position.get("externalId")).isEqualTo("pos-1");
        assertThat(position.get("externalCreatedAt")).isNotNull();
        assertThat(position.get("direction")).isEqualTo("LONG");

        LoggedRequest sent = exchange.single(OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH);
        assertThat(sent.getUrl()).contains("instType=SWAP", "instId=" + INSTRUMENT,
                "before=" + WINDOW_BEGIN_MILLIS, "limit=100");
        assertThat(sent.getUrl()).doesNotContain("after=");
    }

    @Test
    @DisplayName("B5.3 — запись чужого инструмента нарушает инвариант")
    void b5_3_aRecordOfAForeignInstrumentViolatesTheInvariant() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH, Okx.ok(
                Okx.closedPosition(INSTRUMENT).text(),
                Okx.closedPosition(OTHER_INSTRUMENT).text()));

        Answer answer = get(account("/positions/closed?externalInstrumentId=" + INSTRUMENT
                + "&windowBegin=" + WINDOW_BEGIN));

        assertThat(answer.errorCode()).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
        assertThat(String.valueOf(answer.asObject().get("message")))
                .contains(INSTRUMENT, OTHER_INSTRUMENT);
        assertThat(answer.body()).doesNotContain("\"externalId\"");
    }

    /**
     * Ожидание взято из дома: структурная валидация записи закрытия
     * объявлена ТРЕМЯ проверками
     * ({@code docs/models/mapping/PositionCloseResult.md} §«Структурная
     * валидация — до маппинга»). Вариант на каждую форму нарушения:
     * пустая валюта, пустой net, неразбираемое число, неразрешимое
     * направление.
     */
    @Test
    @DisplayName("B5.4 — запись без обязательных полей отвергается там же, где разбирается")
    void b5_4_aRecordWithoutMandatoryFieldsIsRejectedWhereItIsParsed() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH, Okx.ok());
        assertThat(get(account("/positions/closed?externalInstrumentId=" + INSTRUMENT
                + "&windowBegin=" + WINDOW_BEGIN)).asList()).isEmpty();

        List<Okx.Record> violations = List.of(
                Okx.closedPosition(INSTRUMENT).without("ccy"),
                Okx.closedPosition(INSTRUMENT).without("realizedPnl"),
                Okx.closedPosition(INSTRUMENT).with("fee", "not-a-number"),
                Okx.closedPosition(INSTRUMENT).with("direction", "sideways"));
        for (Okx.Record violation : violations) {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH, Okx.ok(violation.text()));

            Answer answer = get(account("/positions/closed?externalInstrumentId=" + INSTRUMENT
                    + "&windowBegin=" + WINDOW_BEGIN));

            assertThat(answer.carriesErrorDto()).as(violation.text()).isTrue();
            assertThat(answer.errorCode()).as(violation.text()).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
        }
    }

    @Test
    @DisplayName("B5.5 — успешное чтение баланса обязано вернуть снапшот")
    void b5_5_aSuccessfulBalanceReadMustReturnASnapshot() {
        exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok());

        Answer empty = get(account("/balance?settleCurrency=USDT"));

        assertThat(empty.errorCode()).isEqualTo("EXCHANGE_ERROR");

        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(
                "{\"uTime\":\"1758240000000\",\"totalEq\":\"1000\",\"adjEq\":\"990\","
                        + "\"availEq\":\"900\",\"details\":[{\"ccy\":\"USDT\",\"eq\":\"1000\","
                        + "\"cashBal\":\"1000\",\"availBal\":\"900\",\"frozenBal\":\"100\"}]}"));

        exchange.answers(OkxConstants.ACCOUNT_CONFIG_PATH, Okx.ok(Okx.accountConfig("2", "net_mode")));

        Answer filled = get(account("/balance?settleCurrency=USDT"));

        assertThat(filled.status()).isEqualTo(200);
        assertThat(filled.body()).isNotBlank();
        assertThat(exchange.single(OkxConstants.ACCOUNT_BALANCE_PATH).getUrl()).contains("ccy=USDT");
    }

    /**
     * Ожидание взято из дома: строка расчётной валюты у баланса обязательна,
     * и её отсутствие — контролируемый отказ границы
     * ({@code docs/models/mapping/Balance.md} §«Validation (структурная, до
     * маппинга)»); класс — нарушение инварианта контракта: недостача
     * обязательного поля ({@code docs/rules/controlled-exchange-exceptions.md}).
     * Вариант на каждую форму недостачи: строка только чужой валюты, пустой
     * перечень строк, перечня нет вовсе.
     */
    @Test
    @DisplayName("B5.10 — ответ баланса без строки расчётной валюты нарушает инвариант")
    void b5_10_aBalanceWithoutTheSettleCurrencyRowViolatesTheInvariant() {
        List<String> violations = List.of(
                balanceAnswer(",\"details\":[{\"ccy\":\"BTC\",\"eq\":\"1\",\"availBal\":\"1\",\"frozenBal\":\"0\"}]"),
                balanceAnswer(",\"details\":[]"),
                balanceAnswer(""));
        for (String violation : violations) {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(violation));

            Answer answer = get(account("/balance?settleCurrency=USDT"));

            assertThat(answer.carriesErrorDto()).as(violation).isTrue();
            assertThat(answer.errorCode()).as(violation).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
            assertThat(String.valueOf(answer.asObject().get("message"))).as(violation).contains("USDT");
        }
    }

    /**
     * Ожидание взято из дома: поля уровня счёта и строки расчётной валюты,
     * объявленные обязательными, заполнены и разбираются
     * ({@code docs/models/mapping/Balance.md} §«Validation (структурная, до
     * маппинга)»); нарушение — та же форма отказа, что у отсутствующей
     * строки. Вариант на каждое обязательное поле и на каждую форму
     * нарушения: пусто, нет вовсе, не разбирается.
     */
    @Test
    @DisplayName("B5.11 — баланс с пустым или неразбираемым обязательным полем нарушает инвариант")
    void b5_11_aBalanceWithABlankOrUnparsedMandatoryFieldViolatesTheInvariant() {
        List<String> violations = List.of(
                balanceOf("\"\"", "\"1000\"", "\"990\"", "\"900\"", row("\"1000\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf(null, "\"1000\"", "\"990\"", "\"900\"", row("\"1000\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf("\"soon\"", "\"1000\"", "\"990\"", "\"900\"", row("\"1000\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"\"", "\"990\"", "\"900\"",
                        row("\"1000\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"abc\"", "\"900\"",
                        row("\"1000\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"abc\"",
                        row("\"1000\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"1000\"", null, "\"900\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"1000\"", "\"1000\"", "\"\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"1000\"", "\"1000\"", "\"nine\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"1000\"", "\"1000\"", "\"900\"", "\"abc\"")));
        for (String violation : violations) {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(violation));

            Answer answer = get(account("/balance?settleCurrency=USDT"));

            assertThat(answer.carriesErrorDto()).as(violation).isTrue();
            assertThat(answer.errorCode()).as(violation).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
            assertThat(String.valueOf(answer.asObject().get("message"))).as(violation).contains("USDT");
        }
    }

    /**
     * Ожидание взято из дома: отрицательные свободный и замороженный остатки
     * строки расчётной валюты запрещены ({@code docs/models/mapping/Balance.md}
     * §«Validation (структурная, до маппинга)», строка «Numeric»). Вариант на
     * каждый остаток.
     */
    @Test
    @DisplayName("B5.12 — отрицательный свободный или замороженный остаток нарушает инвариант")
    void b5_12_aNegativeAvailableOrFrozenBalanceViolatesTheInvariant() {
        List<String> violations = List.of(
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"1000\"", "\"1000\"", "\"-1\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"1000\"", "\"1000\"", "\"900\"", "\"-0.5\"")));
        for (String violation : violations) {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(violation));

            Answer answer = get(account("/balance?settleCurrency=USDT"));

            assertThat(answer.carriesErrorDto()).as(violation).isTrue();
            assertThat(answer.errorCode()).as(violation).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
            assertThat(String.valueOf(answer.asObject().get("message"))).as(violation).contains("USDT");
        }
    }

    /**
     * Обратная сторона B5.11 и B5.12 — граница не отвергает законный ответ
     * ({@code docs/models/mapping/Balance.md} §«Validation (структурная, до
     * маппинга)» и §«OKX validation notes»): пустые скорректированный и
     * свободный капитал счёта (режим счёта, где площадка их не ведёт),
     * пустой замороженный остаток и отрицательные капитал и денежный
     * остаток строки — признак обязательства, который читает преконтроль
     * ядра, а не граница. Отказ здесь ронял бы рефреш баланса такого счёта в
     * аварийный контур.
     */
    @Test
    @DisplayName("B5.13 — пустой капитал вне режима и отрицательные капитал и денежный остаток не отвергаются")
    void b5_13_optionalBlanksAndNegativeEquityAndCashAreNotRejected() {
        List<String> lawful = List.of(
                balanceOf("\"1758240000000\"", "\"1000\"", "\"\"", "\"\"",
                        row("\"1000\"", "\"1000\"", "\"900\"", "\"100\"")),
                balanceOf("\"1758240000000\"", "\"1000\"", null, null,
                        row("\"1000\"", "\"1000\"", "\"900\"", null)),
                balanceOf("\"1758240000000\"", "\"1000\"", "\"990\"", "\"900\"",
                        row("\"-5\"", "\"-5\"", "\"0\"", "\"0\"")));
        for (String answerBody : lawful) {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(answerBody));
            exchange.answers(OkxConstants.ACCOUNT_CONFIG_PATH, Okx.ok(Okx.accountConfig("2", "net_mode")));

            Answer answer = get(account("/balance?settleCurrency=USDT"));

            assertThat(answer.status()).as(answerBody).isEqualTo(200);
            assertThat(answer.carriesErrorDto()).as(answerBody).isFalse();
        }
    }

    /**
     * Ожидание взято из дома: режим счёта и режим позиций приезжают со
     * снимком средств, переведённые в доменный словарь на границе
     * коннектора ({@code docs/models/mapping/Balance.md}
     * §«`AccountConfigOkxResponse` → snapshot»). Вариант на режим контура, на
     * режимы вне контура и на значения вне словаря — последние дают пустоту,
     * а не угаданный режим и не отказ.
     */
    @Test
    @DisplayName("B5.14 — снимок средств несёт режим счёта и режим позиций в доменном словаре")
    void b5_14_theFundsSnapshotCarriesTheAccountAndPositionModes() {
        List<List<String>> variants = List.of(
                List.of("2", "net_mode", "FUTURES", "NET"),
                List.of("3", "long_short_mode", "MULTI_CURRENCY_MARGIN", "LONG_SHORT"),
                List.of("4", "net_mode", "PORTFOLIO_MARGIN", "NET"),
                List.of("1", "net_mode", "SPOT", "NET"),
                List.of("7", "hedge_mode", "null", "null"));
        for (List<String> variant : variants) {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(balanceAnswer(
                    ",\"details\":[{\"ccy\":\"USDT\",\"eq\":\"1000\",\"cashBal\":\"1000\","
                            + "\"availBal\":\"900\",\"frozenBal\":\"100\"}]")));
            exchange.answers(OkxConstants.ACCOUNT_CONFIG_PATH,
                    Okx.ok(Okx.accountConfig(variant.get(0), variant.get(1))));

            Answer answer = get(account("/balance?settleCurrency=USDT"));

            assertThat(answer.status()).as(variant.toString()).isEqualTo(200);
            assertThat(String.valueOf(answer.asObject().get("accountMode"))).as(variant.toString())
                    .isEqualTo(variant.get(2));
            assertThat(String.valueOf(answer.asObject().get("positionMode"))).as(variant.toString())
                    .isEqualTo(variant.get(3));
            LoggedRequest config = exchange.single(OkxConstants.ACCOUNT_CONFIG_PATH);
            assertThat(config.containsHeader(OkxConstants.ACCESS_SIGN_HEADER))
                    .as("конфигурация счёта — приватное чтение с подписью").isTrue();
            assertThat(answer.body()).doesNotContain("acctLv", "posMode", "net_mode", "long_short_mode");
        }
    }

    /**
     * Ожидание взято из дома: пустой ответ конфигурации — отказ того же
     * класса, что пустой баланс, — сведений о счёте нет, и повтор осмыслен
     * ({@code docs/models/mapping/Balance.md} §«Validation (структурная, до
     * маппинга)»); отвергнутый баланс второго запроса не стоит (там же,
     * §«OKX validation notes»).
     */
    @Test
    @DisplayName("B5.15 — пустая конфигурация счёта отказывает, а отвергнутый баланс её не читает")
    void b5_15_anEmptyAccountConfigFailsAndARejectedBalanceDoesNotReadIt() {
        exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(balanceAnswer(
                ",\"details\":[{\"ccy\":\"USDT\",\"eq\":\"1000\",\"cashBal\":\"1000\","
                        + "\"availBal\":\"900\",\"frozenBal\":\"100\"}]")));
        exchange.answers(OkxConstants.ACCOUNT_CONFIG_PATH, Okx.ok());

        Answer empty = get(account("/balance?settleCurrency=USDT"));

        assertThat(empty.carriesErrorDto()).isTrue();
        assertThat(empty.errorCode()).isEqualTo("EXCHANGE_ERROR");

        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_BALANCE_PATH, Okx.ok(balanceAnswer(",\"details\":[]")));
        exchange.answers(OkxConstants.ACCOUNT_CONFIG_PATH, Okx.ok(Okx.accountConfig("2", "net_mode")));

        Answer rejected = get(account("/balance?settleCurrency=USDT"));

        assertThat(rejected.errorCode()).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
        assertThat(exchange.requests(OkxConstants.ACCOUNT_CONFIG_PATH)).isEmpty();
    }

    @Test
    @DisplayName("B5.6 — движения средств обходятся пагинацией назад до пустой страницы")
    void b5_6_billsAreWalkedBackwardsUntilAnEmptyPage() {
        exchange.answersWithout(OkxConstants.ACCOUNT_BILLS_PATH, "after",
                Okx.ok(bill("b-20"), bill("b-19")));
        exchange.answersWhen(OkxConstants.ACCOUNT_BILLS_PATH, "after", "b-19",
                Okx.ok(bill("b-18"), bill("b-17")));
        exchange.answersWhen(OkxConstants.ACCOUNT_BILLS_PATH, "after", "b-17", Okx.ok());

        Answer answer = get(account("/bills?begin=" + WINDOW_BEGIN + "&end=2026-09-02T00:00:00Z"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(4);
        assertThat(answer.body()).doesNotContain("\"after\"", "billId");

        List<LoggedRequest> sent = exchange.requests(OkxConstants.ACCOUNT_BILLS_PATH);
        assertThat(sent).hasSize(3);
        assertThat(sent.getFirst().getUrl()).doesNotContain("after=");
        assertThat(sent.get(1).getUrl()).contains("after=b-19");
        assertThat(sent.get(2).getUrl()).contains("after=b-17");
        sent.forEach(request -> assertThat(request.getUrl()).doesNotContain("ccy="));
    }

    @Test
    @DisplayName("B5.7 — архив движений читается тем же обходом по своему пути")
    void b5_7_theBillsArchiveIsWalkedTheSameWayOnItsOwnPath() {
        exchange.answersWithout(OkxConstants.ACCOUNT_BILLS_ARCHIVE_PATH, "after",
                Okx.ok(bill("a-20"), bill("a-19")));
        exchange.answersWhen(OkxConstants.ACCOUNT_BILLS_ARCHIVE_PATH, "after", "a-19", Okx.ok());

        Answer answer = get(account("/bills/archive?begin=" + WINDOW_BEGIN
                + "&end=2026-09-02T00:00:00Z"));

        assertThat(answer.asList()).hasSize(2);
        assertThat(exchange.requests(OkxConstants.ACCOUNT_BILLS_ARCHIVE_PATH)).hasSize(2);
        assertThat(exchange.requests(OkxConstants.ACCOUNT_BILLS_PATH)).isEmpty();
    }

    @Test
    @DisplayName("B5.8 — ставка комиссии живёт только в группах")
    void b5_8_theFeeRateLivesInGroupsOnly() {
        exchange.answers(OkxConstants.ACCOUNT_TRADE_FEE_PATH, Okx.ok(
                "{\"instType\":\"SWAP\",\"level\":\"Lv1\",\"ts\":\"1758240000000\","
                        + "\"taker\":\"-0.0005\",\"maker\":\"-0.0002\",\"feeGroup\":["
                        + "{\"groupId\":\"1\",\"taker\":\"-0.0005\",\"maker\":\"-0.0002\"},"
                        + "{\"groupId\":\"2\",\"taker\":\"-0.0004\",\"maker\":\"-0.0001\"}]}"));

        Answer answer = get(account("/trade-fee-rates?externalInstrumentType=SWAP"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(2);
        LoggedRequest sent = exchange.single(OkxConstants.ACCOUNT_TRADE_FEE_PATH);
        assertThat(sent.getUrl()).contains("instType=SWAP");
        assertThat(sent.getUrl()).doesNotContain("instId=", "instFamily=");

        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_TRADE_FEE_PATH, Okx.ok(
                "{\"instType\":\"SWAP\",\"level\":\"Lv1\",\"ts\":\"1758240000000\",\"feeGroup\":[]}"));

        assertThat(get(account("/trade-fee-rates?externalInstrumentType=SWAP")).asList()).isEmpty();
    }

    /**
     * Структурная валидация группы ставок — до маппинга
     * ({@code docs/models/mapping/TradeFeeRate.md} §«Validation
     * (структурная, до маппинга)»): пустая и непарсящаяся ставка, пустой
     * ключ группы и неразбираемое время — контролируемый отказ границы, а
     * не молчаливая пустота. Вариант на каждую форму нарушения.
     */
    @Test
    @DisplayName("B5.9 — группа ставок без обязательного поля или с неразбираемым числом отвергается")
    void b5_9_aFeeGroupWithoutMandatoryFieldsIsRejected() {
        List<String> violations = List.of(
                feeAnswer("1758240000000", "{\"groupId\":\"1\",\"taker\":\"\",\"maker\":\"-0.0002\"}"),
                feeAnswer("1758240000000", "{\"groupId\":\"1\",\"taker\":\"-0.0005\"}"),
                feeAnswer("1758240000000", "{\"groupId\":\"\",\"taker\":\"-0.0005\",\"maker\":\"-0.0002\"}"),
                feeAnswer("1758240000000", "{\"groupId\":\"1\",\"taker\":\"abc\",\"maker\":\"-0.0002\"}"),
                feeAnswer("nonsense", "{\"groupId\":\"1\",\"taker\":\"-0.0005\",\"maker\":\"-0.0002\"}"));
        for (String violation : violations) {
            exchange.reset();
            exchange.answers(OkxConstants.ACCOUNT_TRADE_FEE_PATH, Okx.ok(violation));

            Answer answer = get(account("/trade-fee-rates?externalInstrumentType=SWAP"));

            assertThat(answer.carriesErrorDto()).as(violation).isTrue();
            assertThat(answer.errorCode()).as(violation).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
        }
    }

    /** Ответ баланса со счётными полями и названным хвостом строк валют. */
    private static String balanceAnswer(String details) {
        return "{\"uTime\":\"1758240000000\",\"totalEq\":\"1000\",\"adjEq\":\"990\",\"availEq\":\"900\""
                + details + "}";
    }

    /**
     * Ответ баланса с названными значениями полей уровня счёта и одной
     * строкой валют. Значение пишется JSON-литералом (строка — в кавычках);
     * {@code null} — поля в ответе нет вовсе.
     */
    private static String balanceOf(String uTime, String totalEq, String adjEq, String availEq, String row) {
        return "{" + fields("uTime", uTime, "totalEq", totalEq, "adjEq", adjEq, "availEq", availEq)
                + "\"details\":[" + row + "]}";
    }

    /** Строка расчётной валюты {@code USDT} с названными остатками; {@code null} — поля нет. */
    private static String row(String eq, String cashBal, String availBal, String frozenBal) {
        String body = "\"ccy\":\"USDT\",\"uTime\":\"1758200000000\","
                + fields("eq", eq, "cashBal", cashBal, "availBal", availBal, "frozenBal", frozenBal);
        return "{" + body.substring(0, body.length() - 1) + "}";
    }

    /** Пары «ключ, JSON-литерал» через запятую, каждая с хвостовой запятой; пустое значение пропускается. */
    private static String fields(String... keysAndValues) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (nonNull(keysAndValues[i + 1])) {
                text.append('"').append(keysAndValues[i]).append("\":").append(keysAndValues[i + 1]).append(',');
            }
        }
        return text.toString();
    }

    private static String feeAnswer(String ts, String group) {
        return "{\"instType\":\"SWAP\",\"level\":\"Lv1\",\"ts\":\"" + ts + "\",\"feeGroup\":[" + group + "]}";
    }

    /** Запись движения средств с названным идентификатором. */
    private static String bill(String billId) {
        return Okx.record("billId", billId, "type", "2", "subType", "1", "ts", "1758240000000",
                "balChg", "10", "posBalChg", "0", "fee", "-0.1", "ccy", "USDT",
                "ordId", "ord-1", "instId", INSTRUMENT).text();
    }
}
