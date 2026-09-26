package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Stub;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.exitandclose.ExitTrail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Пролог и ходы тропы «расхождение замечено → ступень поднята → живой риск
 * снят» (.claude/tests/cases/e2e-safety-teardown.md).
 *
 * <p><b>Своих ходов постановки у тропы нет:</b> пролог — ходы первой тропы до
 * налива и {@code E1.1} тропы выхода, исполненные {@link ExitTrail}. Здесь —
 * только то, чем тропа начинается сама: срез площадки с расхождением и тик
 * детекции (§«Новая ось формы — ВХОД ТРОПЫ ЕСТЬ РАСХОЖДЕНИЕ, А НЕ ХОД»).
 *
 * <p><b>Срезы счёта и чтения по инструменту идут одними путями</b> — разводит
 * их параметр: счёт-широкое чтение позиций сужено типом инструмента, чтение
 * условных заявок — семьёй. Поэтому расхождение ставится ответом по
 * параметру и штатных чтений по инструменту не трогает.
 */
final class TeardownTrail {

    static final String ORDERS_PENDING = "/api/v5/trade/orders-pending";

    static final String HOLD_RAISED = "HOLD_RAISED";

    static final String ANOMALY_REPORTED = "ANOMALY_REPORTED";

    static final String FOREIGN_ORDER = "EXCHANGE_FOREIGN_ORDER";

    static final String FOREIGN_INSTRUMENT_RISK = "EXCHANGE_FOREIGN_INSTRUMENT_RISK";

    static final String PASS_INCOMPLETE = "ANOMALY_PASS_INCOMPLETE";

    static final String MIN_AGE = "anomaly-job.confirmation-min-age";

    static final String BLIND_PASS_LIMIT = "anomaly-job.blind-pass-limit";

    static final String FOREIGN_INSTRUMENT = "SOL-USDT-SWAP";

    static final String DEAL_SHUTDOWN_INITIATED = "DEAL_SHUTDOWN_INITIATED";

    static final String DEAL_CLOSED = "DEAL_CLOSED";

    static final String TEARDOWN_ATTEMPTS = "kill-switch.max-teardown-attempts";

    static final String MANUAL_REQUESTED = "MANUAL_HALT_REQUESTED";

    static final String MANUAL_CLEARED = "MANUAL_HALT_CLEARED";

    private static final String HALT_FINISHED = "Holder full halt finished";

    private static final String REFUSED_FAMILY = "oco";

    private static final String SECOND_LEG = "second-leg";

    private static final String CLOSE_WITHOUT_EFFECT = "close-without-effect";

    private static final String FOREIGN_POSITION = "foreign-position";

    private static final String CLOSED = "closed";

    private static final String EMPTY = """
            {"code": "0", "msg": "", "data": []}
            """;

    private TeardownTrail() {
    }

    /**
     * Пролог тропы: сделка с налившейся входной ногой и ненулевой
     * экспозицией транша, срез заявок счёта без расхождения.
     *
     * @param trail тропа
     * @return идентичность сделки
     */
    static String walkToExposure(Trail trail) {
        String deal = ExitTrail.walkToExposure(trail, ExitTrail.conditionOnlyExit());
        exchangeHoldsNoForeignOrder(trail);
        return deal;
    }

    /**
     * Пролог с добором: сверх налившейся ноги живы вторая входная нога и
     * отдельная условная заявка защиты; площадка отражает отмену второй ноги
     * — после принятой отмены нога читается снятой, соседние заявки прежними.
     *
     * @param trail тропа
     * @return идентичность сделки
     */
    static String walkToScaledIn(Trail trail) {
        String deal = ExitTrail.walkToScaledIn(trail);
        exchangeHoldsNoForeignOrder(trail);
        Map<String, Object> leg = trail.database(Party.TRADING_CORE)
                .query("select internal_id, size from orders where external_id = ?", ExitTrail.SECOND_ORDER)
                .getFirst();
        trail.exchange().answersPostMoving(ExitTrail.CANCEL_ORDER, "$.ordId", ExitTrail.SECOND_ORDER, SECOND_LEG,
                Stub.STARTED, ExitTrail.CANCELED, """
                {"code": "0", "msg": "", "data": [{"ordId": "%s", "clOrdId": "%s", "sCode": "0", "sMsg": ""}]}
                """.formatted(ExitTrail.SECOND_ORDER, leg.get("internal_id")));
        trail.exchange().answersWhereInState(Trail.EXCHANGE_ORDER, "ordId", ExitTrail.SECOND_ORDER, SECOND_LEG,
                ExitTrail.CANCELED, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "ordId": "%s", "clOrdId": "%s",
                  "ordType": "market", "side": "buy", "posSide": "net", "state": "canceled", "px": "",
                  "sz": "%s", "accFillSz": "0", "avgPx": "", "fee": "0", "feeCcy": "USDT",
                  "cTime": "1758240100000", "uTime": "1758240102000"}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT, ExitTrail.SECOND_ORDER, leg.get("internal_id"),
                ((BigDecimal) leg.get("size")).stripTrailingZeros().toPlainString()));
        return deal;
    }

    /**
     * Площадка принимает закрытие позиции тропы и не исполняет его: позиция
     * читается живой и после принятой команды. Ответ заведён позже ответа
     * пролога с той же приоритетностью и потому его перекрывает.
     */
    static void exchangeAcknowledgesCloseWithoutEffect(Trail trail) {
        trail.exchange().answersPostMoving(ExitTrail.CLOSE_POSITION, "$.instId", Trail.EXTERNAL_INSTRUMENT,
                CLOSE_WITHOUT_EFFECT, Stub.STARTED, Stub.STARTED, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "posSide": "net", "clOrdId": "", "tag": "",
                  "sCode": "0", "sMsg": ""}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT));
    }

    /**
     * Состояние {@code E1.1} этой тропы: чужая заявка в срезе, один тик
     * детекции — строка наблюдения стои́т.
     */
    static void standAtObservation(Trail trail) {
        exchangeHoldsForeignOrder(trail);
        detect(trail);
        trail.relayCore();
    }

    /**
     * Ставит ступень ручной поверхностью ядра: на счёте, либо на паре «счёт,
     * инструмент», если инструмент назван.
     *
     * @param trail      тропа
     * @param haltClass  класс вмешательства
     * @param instrument инструмент пары; пусто — радиус счётный
     * @return ответ поверхности
     */
    static Answer halt(Trail trail, String haltClass, String instrument) {
        String pair = isNull(instrument) ? "" : ", \"instrumentInternalId\": \"" + instrument + "\"";
        return trail.call(Party.TRADING_CORE, "POST", Trail.CORE + "/safety/halts", null, """
                {"haltClass": "%s", "exchangeAccountInternalId": "%s"%s}
                """.formatted(haltClass, trail.account(), pair));
    }

    /**
     * Полная постановка ручной поверхностью и ожидание записи фасада о конце
     * запуска: поверхность отвечает до реакции, и след читается после неё.
     *
     * @param trail      тропа
     * @param instrument инструмент пары; пусто — радиус счётный
     * @return ответ поверхности
     */
    static Answer haltFully(Trail trail, String instrument) {
        Side core = trail.side(Party.TRADING_CORE);
        Long mark = core.logMark();
        Answer answer = halt(trail, "FULL", instrument);
        Trail.await("полная постановка кончилась", () -> core.logSince(mark).contains(HALT_FINISHED));
        return answer;
    }

    /**
     * Снимает названную ступень ручной поверхностью ядра; синхронно.
     *
     * @param trail      тропа
     * @param haltClass  класс снимаемой ступени
     * @param instrument инструмент пары; пусто — радиус счётный
     * @return ответ поверхности
     */
    static Answer clear(Trail trail, String haltClass, String instrument) {
        String pair = isNull(instrument) ? "" : ", \"instrumentInternalId\": \"" + instrument + "\"";
        return trail.call(Party.TRADING_CORE, "POST", Trail.CORE + "/safety/halt-clearances", null, """
                {"haltClass": "%s", "exchangeAccountInternalId": "%s"%s}
                """.formatted(haltClass, trail.account(), pair));
    }

    /**
     * Площадка снова исполняет закрытие позиции тропы и отражает его:
     * ответ, заведённый позже, перекрывает закрытие без исполнения.
     */
    static void exchangeConfirmsClose(Trail trail) {
        ExitTrail.exchangeMirrorsClose(trail);
    }

    /**
     * Сделка тропы доведена после каскада до аварийного терминала проходами
     * сопровождения; площадка отдаёт движения средств пустой страницей.
     *
     * @param trail тропа
     * @param deal  сделка
     */
    static void passUntilEmergencyClosed(Trail trail, String deal) {
        ExitTrail.exchangeKeepsBills(trail, System.currentTimeMillis(), "0", "");
        trail.passUntil("сделка " + deal + " в аварийном терминале",
                () -> Objects.equals("EMERGENCY_CLOSED", dealStatus(trail, deal)));
        trail.relayCore();
    }

    /**
     * Сделка заведена восстановлением на паре тропы после каскада: площадка
     * снова держит живую позицию, которую не объясняет ни одна активная
     * сделка, заявок и условных заявок у счёта нет; тик детекции заводит
     * сделку по позиции.
     *
     * @param trail тропа
     * @return идентичность восстановленной сделки
     */
    static String recoverAfterCascade(Trail trail) {
        exchangeHoldsNoForeignOrder(trail);
        trail.exchange().answers(Trail.EXCHANGE_ALGO_PENDING, EMPTY);
        trail.exchange().forgetScenarios();
        detect(trail);
        trail.relayCore();
        List<String> recovered = trail.database(Party.TRADING_CORE)
                .query("select internal_id from deals where entry_reason = 'RECOVERY' and status = 'ACTIVE'").stream()
                .map(row -> String.valueOf(row.get("internal_id")))
                .toList();
        if (recovered.size() != 1) {
            throw new IllegalStateException("Предусловие не поставлено: восстановленная сделка — " + recovered);
        }
        return recovered.getFirst();
    }

    /** Строки outbox ядра названного класса о сделке — по её идентичности в содержимом. */
    static List<Map<String, Object>> dealOutbox(Trail trail, String eventType, String deal) {
        return trail.database(Party.TRADING_CORE).query("select event_id, occurred_at from outbox_events "
                + "where event_type = ? and payload ->> 'dealInternalId' = ? order by id", eventType, deal);
    }

    /** Статус сделки колонкой ядра. */
    static String dealStatus(Trail trail, String deal) {
        return String.valueOf(trail.database(Party.TRADING_CORE)
                .query("select status from deals where internal_id = ?", deal).getFirst().get("status"));
    }

    /** Тик проактивной детекции ручным фасадом ядра. */
    static void detect(Trail trail) {
        trail.tick(Party.TRADING_CORE, "/anomaly-detection", "Manual AnomalyJob trigger finished");
    }

    /**
     * Срез pending-заявок несёт живую заявку по инструменту тропы, чей
     * клиентский идентификатор маркера контура не несёт.
     */
    static void exchangeHoldsForeignOrder(Trail trail) {
        trail.exchange().answers(ORDERS_PENDING, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "ordId": "okx-hand-1", "clOrdId": "hand1",
                  "ordType": "limit", "side": "sell", "posSide": "net", "state": "live", "px": "2500",
                  "sz": "1", "accFillSz": "0", "avgPx": "", "fee": "0", "feeCcy": "USDT",
                  "cTime": "1758240000000", "uTime": "1758240000000"}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT));
    }

    /** Срез pending-заявок пуст: чужой заявки у счёта нет. */
    static void exchangeHoldsNoForeignOrder(Trail trail) {
        trail.exchange().answers(ORDERS_PENDING, EMPTY);
    }

    /**
     * Площадка отвечает отказом своего словаря на чтение одной семьи
     * условных заявок — срез условных заявок не добыт, остальные два
     * добыты.
     */
    static void exchangeRefusesAlgoSlice(Trail trail) {
        trail.exchange().answersWhere(Trail.EXCHANGE_ALGO_PENDING, "ordType", REFUSED_FAMILY, """
                {"code": "50001", "msg": "Service temporarily unavailable", "data": []}
                """);
    }

    /** Площадка снова отвечает на семью условных заявок, которой отказывала: у счёта таких нет. */
    static void exchangeAnswersAlgoSlice(Trail trail) {
        trail.exchange().answersWhere(Trail.EXCHANGE_ALGO_PENDING, "ordType", REFUSED_FAMILY, EMPTY);
    }

    /**
     * Счёт-широкий срез позиций несёт сверх позиции тропы живую позицию по
     * биржевому имени, строки каталога которому у ядра нет вовсе.
     *
     * @param trail тропа
     * @param size  размер позиции тропы в контрактах
     */
    static void exchangeHoldsForeignInstrumentPosition(Trail trail, String size) {
        trail.exchange().answersWhere(Trail.EXCHANGE_POSITIONS, "instType", "SWAP", """
                {"code": "0", "msg": "", "data": [
                  {"instId": "%s", "instType": "SWAP", "posId": "okx-pos-1", "pos": "%s", "avgPx": "%s",
                   "markPx": "2001", "lever": "10", "mgnMode": "isolated", "posSide": "net", "upl": "0.1",
                   "margin": "20", "liqPx": "1800", "cTime": "1758240000000", "uTime": "1758240001000"},
                  {"instId": "%s", "instType": "SWAP", "posId": "okx-pos-foreign", "pos": "3", "avgPx": "150",
                   "markPx": "150", "lever": "5", "mgnMode": "isolated", "posSide": "net", "upl": "0",
                   "margin": "90", "liqPx": "100", "cTime": "1758240000000", "uTime": "1758240001000"}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT, size, Trail.ENTRY_PRICE, FOREIGN_INSTRUMENT));
    }

    /**
     * Живой риск счёта — одна позиция по биржевому имени вне контура; сделок,
     * заявок и условных заявок у счёта нет. Площадка принимает закрытие этой
     * позиции и отражает его: после принятой команды срез позиций пуст.
     */
    static void exchangeHoldsOnlyForeignPosition(Trail trail) {
        Stub exchange = trail.exchange();
        exchange.forgetScenarios();
        exchangeHoldsNoForeignOrder(trail);
        exchange.answers(Trail.EXCHANGE_ALGO_PENDING, EMPTY);
        exchange.answersWhere(Trail.EXCHANGE_POSITIONS, "instType", "SWAP", """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "instType": "SWAP", "posId": "okx-pos-foreign",
                  "pos": "3", "avgPx": "150", "markPx": "150", "lever": "5", "mgnMode": "isolated", "posSide": "net",
                  "upl": "0", "margin": "90", "liqPx": "100", "cTime": "1758240000000", "uTime": "1758240001000"}]}
                """.formatted(FOREIGN_INSTRUMENT));
        exchange.answersPostMoving(ExitTrail.CLOSE_POSITION, "$.instId", FOREIGN_INSTRUMENT, FOREIGN_POSITION,
                Stub.STARTED, CLOSED, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "posSide": "net", "clOrdId": "", "tag": "",
                  "sCode": "0", "sMsg": ""}]}
                """.formatted(FOREIGN_INSTRUMENT));
        exchange.answersWhereInState(Trail.EXCHANGE_POSITIONS, "instType", "SWAP", FOREIGN_POSITION, CLOSED,
                EMPTY);
    }

    /** Торговое состояние счёта тропы поверхностью ядра. */
    static JsonNode safetyState(Trail trail) {
        Answer answer = trail.call(Party.TRADING_CORE, "GET", Trail.CORE + "/safety/states/" + trail.account(),
                trail.tenant(), null);
        if (answer.status() != 200) {
            throw new IllegalStateException("Чтение торгового состояния — " + answer.status() + " " + answer.body());
        }
        return Json.tree(answer.body());
    }

    /** Строки отчёта аномалий ядра по коду — в порядке заведения. */
    static List<Map<String, Object>> reports(Trail trail, String code) {
        return trail.database(Party.TRADING_CORE).query("select internal_id, scope, severity, status, "
                + "instrument_id, created_at from anomaly_reports where code = ? order by id", code);
    }

    /** Строки outbox ядра названного класса — в порядке заведения. */
    static List<Map<String, Object>> outbox(Trail trail, String eventType) {
        return trail.database(Party.TRADING_CORE).query("select event_id, tenant_id, occurred_at, "
                + "payload::text as payload, published_at from outbox_events where event_type = ? order by id",
                eventType);
    }

    /**
     * Чтения срезов счёта после последнего забывания: позиции с сужением
     * типом инструмента, pending-заявки и pending условные заявки по семьям.
     */
    static List<LoggedRequest> sliceReads(Trail trail) {
        return trail.exchange().requests().stream()
                .filter(request -> Objects.equals("GET", request.getMethod().getName()))
                .filter(request -> {
                    String path = request.getUrl().split("\\?")[0];
                    return Objects.equals(ORDERS_PENDING, path)
                            || (Objects.equals(Trail.EXCHANGE_POSITIONS, path)
                            && request.queryParameter("instType").isPresent())
                            || (Objects.equals(Trail.EXCHANGE_ALGO_PENDING, path)
                            && request.queryParameter("ordType").isPresent());
                })
                .toList();
    }

    /** Команды площадке после последнего забывания: всё, что не чтение. */
    static List<String> commands(Trail trail) {
        return trail.exchange().requests().stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .map(LoggedRequest::getUrl)
                .toList();
    }

    /** Строка журнала события — когда она доехала. */
    static Map<String, Object> awaitJournalRow(Trail trail, Object eventId) {
        Database audit = trail.database(Party.AUDIT);
        Trail.await("строка журнала события " + eventId,
                () -> audit.query("select id from audit_records where event_id = ?", String.valueOf(eventId))
                        .size() == 1);
        return audit.query("select * from audit_records where event_id = ?", String.valueOf(eventId)).getFirst();
    }

    /**
     * Строка зерна происшествий суток тропы, сложенная после того, как
     * статистика приняла каждое опубликованное событие несомых классов
     * ядра: числа читаются тогда, когда им больше некуда расти.
     *
     * <p><b>Такт пересчёта — расписанием</b>, сокращённым конфигурацией
     * процесса: фасада у пересчёта нет намеренно, а проход есть проекция
     * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
     * накопитель»).
     *
     * @param trail тропа
     * @return строка агрегата
     */
    static Map<String, Object> incidents(Trail trail) {
        Set<String> published = new HashSet<>();
        trail.database(Party.TRADING_CORE).query("select event_id from outbox_events where published_at is not null "
                        + "and event_type in ('HOLD_RAISED', 'ANOMALY_REPORTED', 'DEAL_OPENED', 'ORDER_DECIDED')")
                .forEach(row -> published.add(String.valueOf(row.get("event_id"))));
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("статистика приняла опубликованные события ядра", () -> {
            Set<String> received = new HashSet<>();
            statistics.query("select event_id from incident_facts")
                    .forEach(row -> received.add(String.valueOf(row.get("event_id"))));
            return received.containsAll(published);
        });
        Instant mark = Instant.now();
        String sql = "select * from incident_aggregates where tenant_id = ? and bucket_date = ?";
        Trail.await("такт пересчёта сложил сутки после приёма", () -> {
            List<Map<String, Object>> rows = statistics.query(sql, trail.tenant(), LocalDate.now(ZoneOffset.UTC));
            return isFalse(rows.isEmpty()) && instant(rows.getFirst().get("assembled_at")).isAfter(mark);
        });
        return statistics.query(sql, trail.tenant(), LocalDate.now(ZoneOffset.UTC)).getFirst();
    }

    /** Число счётчика строки агрегата. */
    static Integer counter(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    /** Содержимое строки outbox. */
    static JsonNode payload(Map<String, Object> outboxRow) {
        return Json.tree(String.valueOf(outboxRow.get("payload")));
    }

    static Boolean absent(JsonNode node) {
        return node.isMissingNode() || node.isNull();
    }

    private static Instant instant(Object column) {
        if (column instanceof java.sql.Timestamp moment) {
            return moment.toInstant();
        }
        return nonNull(column) ? ((OffsetDateTime) column).toInstant() : Instant.EPOCH;
    }
}
