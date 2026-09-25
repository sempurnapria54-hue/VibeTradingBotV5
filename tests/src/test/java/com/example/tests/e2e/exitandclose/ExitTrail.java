package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Пролог и ходы тропы «выход → сверка → терминал»
 * (.claude/tests/cases/e2e-exit-and-close.md §«Новая ось формы — ПРОЛОГ ЧУЖОЙ
 * ТРОПЫ»).
 *
 * <p><b>Пролог — ходы первой тропы до её {@code E5.1}</b>: определение
 * активировано, копия у ядра есть, сделка заведена, входная нога налилась.
 * Экспозиции в зеркале ядра он не оставляет — её ставит первый отрезок этой
 * тропы.
 */
final class ExitTrail {

    static final String DEAL_SHUTDOWN_INITIATED = "DEAL_SHUTDOWN_INITIATED";

    static final String HOLD_RAISED = "HOLD_RAISED";

    static final String HALTS = Trail.CORE + "/safety/halts";

    static final String EXTERNAL_POSITION = "okx-pos-1";

    static final String CANCEL_ORDER = "/api/v5/trade/cancel-order";

    static final String CLOSE_POSITION = "/api/v5/trade/close-position";

    static final String CANCEL_ALGOS = "/api/v5/trade/cancel-algos";

    private ExitTrail() {
    }

    /**
     * Пролог на свежем развёртывании ядра: сделка тропы с налившейся входной
     * ногой и без экспозиции в зеркале.
     *
     * @param trail тропа
     * @return идентичность сделки
     */
    static String walkToFilledEntry(Trail trail) {
        trail.exchangeAcceptsCommands();
        exchangeAcceptsTeardown(trail);
        trail.withoutDeals();
        trail.activeDefinition(conditionOnlyExit());
        String deal = trail.openDeal();
        trail.entrySubmitted();
        trail.relayCore();
        trail.exchangeFillsEntry();
        trail.passUntil("пролог: налив наблюдён", () -> isFalse(trail.database(Party.TRADING_CORE)
                .query("select id from orders where external_status = 'filled'").isEmpty()));
        trail.relayCore();
        return deal;
    }

    /**
     * Эталон, у которого шаг выхода уровня сделки несёт только условие:
     * действия у шага сняты, и команду закрытия эмитит обработчик выхода
     * сделки, а не исполнитель действия — форма, которую пинит документ
     * тропы (.claude/tests/cases/e2e-exit-and-close.md, пробел {@code G3};
     * docs/rules/no-partial-close.md §«Две законные формы полного выхода»).
     *
     * @return тело определения
     */
    static String conditionOnlyExit() {
        JsonNode definition = Json.tree(Trail.referenceDefinition());
        definition.path("details").forEach(detail -> detail.path("stepsByStatus").path("ACTIVE")
                .forEach(step -> {
                    if (Objects.equals("EXIT", step.path("stepType").asString())) {
                        ((ObjectNode) step).remove("actions");
                    }
                }));
        return definition.toString();
    }

    /** Размер налившейся входной ноги — в контрактах, как его несёт зеркало. */
    static String filledSize(Trail trail) {
        return plain(trail.database(Party.TRADING_CORE)
                .query("select size from orders where external_status = 'filled'").getFirst().get("size"));
    }

    /**
     * Стаб площадки отвечает на чтение позиции инструмента живой позицией
     * названного размера — открытой филлом входной ноги.
     *
     * @param trail тропа
     * @param size  размер позиции в контрактах
     */
    static void exchangeHoldsPosition(Trail trail, String size) {
        trail.exchange().answers(Trail.EXCHANGE_POSITIONS, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "instType": "SWAP", "posId": "%s",
                  "pos": "%s", "avgPx": "%s", "markPx": "2001", "lever": "10", "mgnMode": "isolated",
                  "posSide": "net", "upl": "0.1", "margin": "20", "liqPx": "1800",
                  "cTime": "1758240000000", "uTime": "1758240001000"}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT, EXTERNAL_POSITION, size, Trail.ENTRY_PRICE));
    }

    /**
     * Состояние {@code E1.1}: стаб отдаёт позицию размером налившейся ноги, и
     * проходы сопровождения идут, пока экспозиция транша не станет ненулевой.
     *
     * <p><b>Проходы, а не тик:</b> проход дробит работу по звену за раз —
     * наблюдение позиции, затем пересчёт наливов транша, — и экспозицию,
     * которую читает поверхность, пишет проход, следующий за наблюдением.
     *
     * @param trail тропа
     * @param deal  сделка
     */
    static void standAtExposure(Trail trail, String deal) {
        exchangeHoldsPosition(trail, filledSize(trail));
        trail.passUntil("экспозиция транша ненулевая", () -> dealRead(trail, deal).path("tranches").get(0)
                .path("exposure").decimalValue().signum() > 0);
    }

    /**
     * Проходы сопровождения, пока статус сделки не сменится; журналы стабов
     * забываются перед каждым проходом, и после возврата в них — след только
     * прохода, сменившего статус.
     *
     * @param trail  тропа
     * @param deal   сделка
     * @param status статус, с которого сделка уходит
     */
    static void passUntilLeaves(Trail trail, String deal, String status) {
        long deadline = System.currentTimeMillis() + 90_000;
        while (Objects.equals(status, dealRead(trail, deal).path("status").asString())) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Сделка не ушла из " + status + " проходами за 90 секунд");
            }
            trail.forgetTraces();
            trail.orchestrate();
        }
    }

    /**
     * Стаб площадки принимает команды снятия риска: отмену заявки, закрытие
     * позиции и снятие условных заявок.
     *
     * @param trail тропа
     */
    static void exchangeAcceptsTeardown(Trail trail) {
        trail.exchange().answersPostTemplated(CANCEL_ORDER, """
                {"code": "0", "msg": "", "data": [{"ordId": "{{jsonPath request.body '$.ordId'}}",
                  "clOrdId": "{{jsonPath request.body '$.clOrdId'}}", "sCode": "0", "sMsg": ""}]}
                """);
        trail.exchange().answersPost(CLOSE_POSITION, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "posSide": "net", "clOrdId": "", "tag": "",
                  "sCode": "0", "sMsg": ""}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT));
        trail.exchange().answersPost(CANCEL_ALGOS, """
                {"code": "0", "msg": "", "data": [{"algoId": "%s", "sCode": "0", "sMsg": ""}]}
                """.formatted(Trail.EXTERNAL_PROTECTION));
    }

    /**
     * Сделка поверхностью ядра.
     *
     * @param trail тропа
     * @param deal  идентичность сделки
     * @return ответ поверхности
     */
    static JsonNode dealRead(Trail trail, String deal) {
        Answer answer = trail.call(Party.TRADING_CORE, "GET", Trail.CORE + "/deals/" + deal, trail.tenant(), null);
        if (answer.status() != 200) {
            throw new IllegalStateException("Чтение сделки ядра — " + answer.status() + " " + answer.body());
        }
        return Json.tree(answer.body());
    }

    /**
     * Поднимает ступень биржевого радиуса ручной поверхностью ядра.
     *
     * @param trail     тропа
     * @param haltClass класс вмешательства
     * @return ответ поверхности
     */
    static Answer raiseHalt(Trail trail, String haltClass) {
        return trail.call(Party.TRADING_CORE, "POST", HALTS, null, """
                {"haltClass": "%s", "exchangeAccountInternalId": "%s"}
                """.formatted(haltClass, trail.account()));
    }

    /** Строки outbox ядра названного класса по сделке. */
    static List<Map<String, Object>> coreOutbox(Trail trail, String eventType, String deal) {
        return trail.database(Party.TRADING_CORE).query("select event_id, occurred_at, payload::text as payload, "
                + "published_at from outbox_events where event_type = ? and payload ->> 'dealInternalId' = ? "
                + "order by id", eventType, deal);
    }

    /** Строки журнала о сделке. */
    static List<Map<String, Object>> journalOf(Trail trail, String deal) {
        return trail.database(Party.AUDIT).query(
                "select event_type, occurred_at, content::text as content from audit_records "
                        + "where deal_internal_id = ? order by occurred_at", deal);
    }

    static Long count(Trail trail, Party party, String table) {
        Database database = trail.database(party);
        return database.count(table);
    }

    static String plain(Object number) {
        return ((BigDecimal) number).stripTrailingZeros().toPlainString();
    }
}
