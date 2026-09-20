package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
     * валидация — до маппинга»), построена одна — принадлежность
     * инструменту (находка {@code F-3} документа). Пустая расчётная
     * валюта сегодня уезжает наружу как факт; долг —
     * `.claude/work/backlog.md` §«Структурная валидация записи закрытия
     * построена на одну проверку из трёх».
     */
    @Test
    @Tag("debt")
    @DisplayName("B5.4 — запись без обязательных полей отвергается там же, где разбирается")
    void b5_4_aRecordWithoutMandatoryFieldsIsRejectedWhereItIsParsed() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH, Okx.ok());
        assertThat(get(account("/positions/closed?externalInstrumentId=" + INSTRUMENT
                + "&windowBegin=" + WINDOW_BEGIN)).asList()).isEmpty();

        exchange.reset();
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_HISTORY_PATH,
                Okx.ok(Okx.closedPosition(INSTRUMENT).without("ccy").text()));

        Answer answer = get(account("/positions/closed?externalInstrumentId=" + INSTRUMENT
                + "&windowBegin=" + WINDOW_BEGIN));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("EXTERNAL_INVARIANT_VIOLATION");
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
                        + "\"availBal\":\"900\",\"frozenBal\":\"100\"}]}"));

        Answer filled = get(account("/balance?settleCurrency=USDT"));

        assertThat(filled.status()).isEqualTo(200);
        assertThat(filled.body()).isNotBlank();
        assertThat(exchange.single(OkxConstants.ACCOUNT_BALANCE_PATH).getUrl()).contains("ccy=USDT");
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

    /** Запись движения средств с названным идентификатором. */
    private static String bill(String billId) {
        return Okx.record("billId", billId, "type", "2", "subType", "1", "ts", "1758240000000",
                "balChg", "10", "posBalChg", "0", "fee", "-0.1", "ccy", "USDT",
                "ordId", "ord-1", "instId", INSTRUMENT).text();
    }
}
