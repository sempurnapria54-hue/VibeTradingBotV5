package com.example.connector.okx.box;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Обязательный вход не предъявлен: по единице — группа {@code B12}
 * документа `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Один параметризованный метод, а не метод на строку.</b> Отказ
 * производит контейнер — биндинг и валидация аргумента, раньше тела метода
 * контроллера, — и механизм у всех строк один; различает их ровно опущенная
 * единица. Метка строки стои́т первым аргументом и печатается в имени
 * случая: {@code {index}} метки не даёт — счёт идёт от единицы внутри метода.
 *
 * <p><b>Строка несёт пару вызовов, и пара несущая.</b> Вызов без единицы
 * отвергнут контейнером; тот же вызов с единицей контейнером не отвергнут.
 * Без второй половины красный ответ первой не отличался бы от точки,
 * отвергающей всякий вызов, — от опечатки в пути или в значении соседнего
 * входа. Что отвечает полный вызов сверх этого, клетка не утверждает: стаб
 * площадки на него не настроен, и исход у него — предмет прямых кейсов.
 *
 * <p><b>Наблюдатели меряют вызов без единицы и только его.</b> Стаб площадки
 * забывает записи перед каждой строкой — у параметризованного метода обвязка
 * идёт на каждый случай, — и «к площадке запросов нет» утверждается от начала
 * строки; хранилище не опустошается, и чтения пишутся приростом. Полный вызов
 * идёт после замера: он до площадки доезжает законно.
 *
 * <p><b>Тело опускается пустым телом запроса</b> — {@link #post} шлёт его
 * длиной ноль, и для контейнера это «тела нет».
 */
class RequiredInputBoxTest extends SharedConnectorBox {

    private static final String GET = "GET";

    private static final String POST = "POST";

    private static final String NO_BODY = "";

    private static final String INSTRUMENT_PARAM = "externalInstrumentId=" + INSTRUMENT;

    private static final String CONDITION_TYPE = "STOP_LOSS";

    private static final String WINDOW_BEGIN = "2026-10-01T00:00:00Z";

    private static final String WINDOW_END = "2026-10-02T00:00:00Z";

    private static final String REQUEST_NOT_ACCEPTED = "REQUEST_NOT_ACCEPTED";

    @ParameterizedTest(name = "{0} — {1} {2}: без единицы отвергнут контейнером")
    @MethodSource("units")
    @DisplayName("B12 — обязательный вход не предъявлен: по единице")
    void b12_aMissingRequiredInputIsRefusedByTheContainer(String label, String point, String unit,
                                                         String omittedPath, String omittedBody,
                                                         String completePath, String completeBody) {
        Integer readsBefore = secrets.reads();

        Answer omitted = call(point, omittedPath, omittedBody);

        assertThat(omitted.status()).as(label).isEqualTo(400);
        assertThat(omitted.carriesErrorDto()).as(label).isTrue();
        assertThat(omitted.errorCode()).as(label).isEqualTo(REQUEST_NOT_ACCEPTED);
        assertThat(exchange.count()).as(label).isEqualTo(0);
        assertThat(secrets.reads() - readsBefore).as(label).isEqualTo(0);

        Answer complete = call(point, completePath, completeBody);

        assertThat(refusedByContainer(complete)).as(label + " без опущения: " + complete.body()).isFalse();
    }

    private static Stream<Arguments> units() {
        return Stream.of(
                command("B12.1-S", "/orders", "параметр externalInstrumentId",
                        "/orders", Bodies.limitOrder(), "/orders?" + INSTRUMENT_PARAM, Bodies.limitOrder()),
                command("B12.2-S", "/orders", "тело",
                        "/orders?" + INSTRUMENT_PARAM, NO_BODY, "/orders?" + INSTRUMENT_PARAM, Bodies.limitOrder()),
                command("B12.3-S", "/orders/cancellations", "параметр externalInstrumentId",
                        "/orders/cancellations", Bodies.orderToCancel(),
                        "/orders/cancellations?" + INSTRUMENT_PARAM, Bodies.orderToCancel()),
                command("B12.4-S", "/orders/cancellations", "тело",
                        "/orders/cancellations?" + INSTRUMENT_PARAM, NO_BODY,
                        "/orders/cancellations?" + INSTRUMENT_PARAM, Bodies.orderToCancel()),
                command("B12.5-S", "/algo-orders", "параметр externalInstrumentId",
                        "/algo-orders", Bodies.algoOrderToPlace(CONDITION_TYPE),
                        "/algo-orders?" + INSTRUMENT_PARAM, Bodies.algoOrderToPlace(CONDITION_TYPE)),
                command("B12.6-S", "/algo-orders", "тело",
                        "/algo-orders?" + INSTRUMENT_PARAM, NO_BODY,
                        "/algo-orders?" + INSTRUMENT_PARAM, Bodies.algoOrderToPlace(CONDITION_TYPE)),
                command("B12.7-S", "/algo-orders/cancellations", "параметр externalInstrumentId",
                        "/algo-orders/cancellations", Bodies.algoOrderToCancel(CONDITION_TYPE),
                        "/algo-orders/cancellations?" + INSTRUMENT_PARAM, Bodies.algoOrderToCancel(CONDITION_TYPE)),
                command("B12.8-S", "/algo-orders/cancellations", "тело",
                        "/algo-orders/cancellations?" + INSTRUMENT_PARAM, NO_BODY,
                        "/algo-orders/cancellations?" + INSTRUMENT_PARAM, Bodies.algoOrderToCancel(CONDITION_TYPE)),
                command("B12.9-S", "/attached-protections/cancellations", "параметр externalInstrumentId",
                        "/attached-protections/cancellations", Bodies.attachedProtectionToCancel(),
                        "/attached-protections/cancellations?" + INSTRUMENT_PARAM,
                        Bodies.attachedProtectionToCancel()),
                command("B12.10-S", "/attached-protections/cancellations", "тело",
                        "/attached-protections/cancellations?" + INSTRUMENT_PARAM, NO_BODY,
                        "/attached-protections/cancellations?" + INSTRUMENT_PARAM,
                        Bodies.attachedProtectionToCancel()),
                command("B12.11-S", "/positions/closures", "поле формы externalInstrumentId",
                        "/positions/closures", NO_BODY, "/positions/closures?" + INSTRUMENT_PARAM, NO_BODY),
                command("B12.12-S", "/leverage", "параметр externalInstrumentId",
                        "/leverage?leverage=5", NO_BODY, "/leverage?" + INSTRUMENT_PARAM + "&leverage=5", NO_BODY),
                command("B12.13-S", "/leverage", "параметр leverage",
                        "/leverage?" + INSTRUMENT_PARAM, NO_BODY,
                        "/leverage?" + INSTRUMENT_PARAM + "&leverage=5", NO_BODY),
                accountRead("B12.14-S", "/orders/pending/instrument", "параметр externalInstrumentId",
                        "", INSTRUMENT_PARAM),
                accountRead("B12.15-S", "/orders/history", "параметр externalInstrumentId",
                        "", INSTRUMENT_PARAM),
                accountRead("B12.16-S", "/algo-orders/pending/instrument", "параметр externalInstrumentId",
                        "conditionType=" + CONDITION_TYPE, INSTRUMENT_PARAM + "&conditionType=" + CONDITION_TYPE),
                accountRead("B12.17-S", "/algo-orders/pending/instrument", "параметр conditionType",
                        INSTRUMENT_PARAM, INSTRUMENT_PARAM + "&conditionType=" + CONDITION_TYPE),
                accountRead("B12.18-S", "/attached-protections/pending", "параметр externalInstrumentId",
                        "", INSTRUMENT_PARAM),
                accountRead("B12.19-S", "/attached-protections/history", "параметр externalInstrumentId",
                        "leg=EFFECTIVE", INSTRUMENT_PARAM + "&leg=EFFECTIVE"),
                accountRead("B12.20-S", "/attached-protections/history", "параметр leg",
                        INSTRUMENT_PARAM, INSTRUMENT_PARAM + "&leg=EFFECTIVE"),
                accountRead("B12.21-S", "/positions/closed", "параметр externalInstrumentId",
                        "windowBegin=" + WINDOW_BEGIN, INSTRUMENT_PARAM + "&windowBegin=" + WINDOW_BEGIN),
                accountRead("B12.22-S", "/positions/closed", "параметр windowBegin",
                        INSTRUMENT_PARAM, INSTRUMENT_PARAM + "&windowBegin=" + WINDOW_BEGIN),
                accountRead("B12.23-S", "/positions/instrument", "параметр externalInstrumentId",
                        "", INSTRUMENT_PARAM),
                accountRead("B12.24-S", "/balance", "параметр settleCurrency",
                        "", "settleCurrency=USDT"),
                accountRead("B12.25-S", "/bills", "параметр begin",
                        "end=" + WINDOW_END, "begin=" + WINDOW_BEGIN + "&end=" + WINDOW_END),
                accountRead("B12.26-S", "/bills", "параметр end",
                        "begin=" + WINDOW_BEGIN, "begin=" + WINDOW_BEGIN + "&end=" + WINDOW_END),
                accountRead("B12.27-S", "/bills/archive", "параметр begin",
                        "end=" + WINDOW_END, "begin=" + WINDOW_BEGIN + "&end=" + WINDOW_END),
                accountRead("B12.28-S", "/bills/archive", "параметр end",
                        "begin=" + WINDOW_BEGIN, "begin=" + WINDOW_BEGIN + "&end=" + WINDOW_END),
                accountRead("B12.29-S", "/trade-fee-rates", "параметр externalInstrumentType",
                        "", "externalInstrumentType=SWAP"),
                marketRead("B12.30-S", "/instruments", "параметр externalInstrumentType",
                        "", "externalInstrumentType=SWAP"),
                marketRead("B12.31-S", "/instruments/" + INSTRUMENT, "параметр externalInstrumentType",
                        "", "externalInstrumentType=SWAP"),
                marketRead("B12.32-S", "/instruments/" + INSTRUMENT + "/rules", "параметр externalInstrumentType",
                        "", "externalInstrumentType=SWAP"),
                marketRead("B12.33-S", "/tickers", "параметр externalInstrumentType",
                        "", "externalInstrumentType=SWAP"),
                marketRead("B12.34-S", "/order-book/" + INSTRUMENT, "параметр depth",
                        "", "depth=5"),
                marketRead("B12.35-S", "/mark-prices", "параметр externalInstrumentType",
                        "", "externalInstrumentType=SWAP"),
                marketRead("B12.36-S", "/index-prices", "параметр quoteCurrency",
                        "", "quoteCurrency=USDT"),
                accountRead("B12.37-S", "/orders/lookup", "оба идентификатора заявки",
                        INSTRUMENT_PARAM + "&externalId=&internalId=",
                        INSTRUMENT_PARAM + "&externalId=" + Bodies.ORDER_EXTERNAL_ID
                                + "&internalId=" + Bodies.ORDER_INTERNAL_ID),
                accountRead("B12.38-S", "/algo-orders/lookup", "оба идентификатора условной заявки",
                        INSTRUMENT_PARAM + "&externalId=&internalId=",
                        INSTRUMENT_PARAM + "&externalId=" + Bodies.ALGO_EXTERNAL_ID
                                + "&internalId=" + Bodies.ALGO_INTERNAL_ID));
    }

    /** Команда площадке: {@code POST} приватной точки счёта. */
    private static Arguments command(String label, String point, String unit, String omittedPath,
                                     String omittedBody, String completePath, String completeBody) {
        return Arguments.of(label, POST + " " + point, unit, account(omittedPath), omittedBody,
                account(completePath), completeBody);
    }

    /** Приватное чтение: {@code GET} точки счёта; запрос без единицы и полный. */
    private static Arguments accountRead(String label, String point, String unit, String omittedQuery,
                                         String completeQuery) {
        return Arguments.of(label, GET + " " + point, unit, account(withQuery(point, omittedQuery)), null,
                account(withQuery(point, completeQuery)), null);
    }

    /** Публичное чтение: {@code GET} рыночной точки; запрос без единицы и полный. */
    private static Arguments marketRead(String label, String point, String unit, String omittedQuery,
                                        String completeQuery) {
        return Arguments.of(label, GET + " " + point, unit, market(withQuery(point, omittedQuery)), null,
                market(withQuery(point, completeQuery)), null);
    }

    private static String withQuery(String point, String query) {
        if (isEmpty(query)) {
            return point;
        }
        return point + "?" + query;
    }

    /** Вызов точки её методом: метод — первое слово названия точки. */
    private Answer call(String point, String path, String body) {
        if (point.startsWith(POST)) {
            return post(path, body);
        }
        return get(path);
    }

    /** Отверг ли вызов контейнер: {@code 400} с классом отказа контейнера. */
    private static Boolean refusedByContainer(Answer answer) {
        return Objects.equals(answer.status(), 400)
                && isTrue(answer.carriesErrorDto())
                && REQUEST_NOT_ACCEPTED.equals(answer.errorCode());
    }
}
