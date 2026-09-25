package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Stub;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

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

    static final String ORDER_ALGO = "/api/v5/trade/order-algo";

    static final String ALGO_HISTORY = "/api/v5/trade/orders-algo-history";

    static final String POSITIONS_HISTORY = "/api/v5/account/positions-history";

    static final String BILLS = "/api/v5/account/bills";

    static final String BILLS_ARCHIVE = "/api/v5/account/bills-archive";

    static final String INDEX_CANDLES = "/api/v5/market/history-index-candles";

    static final String EXTERNAL_OCO = "okx-oco-1";

    static final String SECOND_ORDER = "okx-order-2";

    static final String CANCELED = "canceled";

    private static final String POSITION_SCENARIO = "position";

    private static final String OCO_SCENARIO = "oco";

    private static final String ATTACHED_SCENARIO = "attached";

    private static final String FLAT = "flat";

    private static final String FIRST = "first";

    private static final String SECOND = "second";

    private static final String BOTH = "both";

    private ExitTrail() {
    }

    /**
     * Пролог с добором: сделка тропы, у транша которой налилась входная нога,
     * стоят отдельная и встроенная защиты, наблюдена экспозиция, а вторая
     * входная нога добора отправлена и жива (.claude/tests/cases/e2e-exit-and-close.md
     * §«E2.1 — Отмена живой входной ноги доходит до стаба раньше закрытия
     * позиции», предусловия).
     *
     * <p><b>Площадка ведёт себя сценариями:</b> принятое закрытие позиции
     * делает её плоской, принятое снятие защиты — снятой; меняет их принятая
     * команда, а не тест, иначе порядок звеньев одного прохода подменялся бы
     * порядком ходов теста.
     *
     * @param trail тропа
     * @return идентичность сделки
     */
    static String walkToScaledIn(Trail trail) {
        trail.exchangeAcceptsCommands();
        exchangeAcceptsTeardown(trail);
        trail.exchange().forgetScenarios();
        trail.withoutDeals();
        trail.activeDefinition(scaleInDefinition());
        String deal = trail.openDeal();
        trail.entrySubmitted();
        trail.relayCore();
        trail.exchangeFillsEntry();
        String size = plain(trail.database(Party.TRADING_CORE)
                .query("select size from orders where external_id = ?", Trail.EXTERNAL_ORDER).getFirst().get("size"));
        exchangeMirrorsProtection(trail, size);
        exchangeMirrorsClose(trail);
        exchangeHoldsSecondLeg(trail, size, "live");
        trail.passUntil("пролог: налив наблюдён", () -> isFalse(trail.database(Party.TRADING_CORE)
                .query("select id from orders where external_status = 'filled'").isEmpty()));
        trail.relayCore();
        exchangeHoldsPosition(trail, size);
        trail.passUntil("вторая входная нога добора отправлена", () -> isFalse(trail.database(Party.TRADING_CORE)
                .query("select id from orders where external_id = ? and external_status = 'live'", SECOND_ORDER)
                .isEmpty()));
        trail.relayCore();
        return deal;
    }

    /**
     * Сделка с добором доведена выходом до терминальности траншей: площадка
     * отдаёт на историю закрытых эпизодов названные записи, движения окна и
     * время площадки; вторая нога отменена и читается снятой
     * (.claude/tests/cases/e2e-exit-and-close.md §«E3.1 — Подтверждение
     * отсутствия живого риска добывается ПО ОДНОЙ сущности за проход, а не
     * срезом счёта», предусловия).
     *
     * <p><b>Время площадки на прологе — настоящее:</b> сканер входа читает
     * его, а момент, названный движениям окна, бывает и прошлым.
     *
     * @param trail      тропа
     * @param records    записи закрытия через запятую; пусто — истории нет
     * @param sourceTime время площадки на добыче движений, мс
     * @param lastBill   идентификатор последней записи страницы движений
     * @param bills      записи движений через запятую
     * @return идентичность сделки
     */
    static String walkToTerminalTranches(Trail trail, String records, Long sourceTime, String lastBill,
                                         String bills) {
        trail.exchange().answers(Trail.EXCHANGE_TIME, """
                {"code": "0", "msg": "", "data": [{"ts": "%d"}]}
                """.formatted(System.currentTimeMillis()));
        String deal = walkToScaledIn(trail);
        Database core = trail.database(Party.TRADING_CORE);
        String size = plain(core.query("select size from orders where external_id = ?", SECOND_ORDER).getFirst()
                .get("size"));
        exchangeKeepsCloseRecords(trail, records);
        exchangeKeepsBills(trail, sourceTime, lastBill, bills);
        trail.marketPhaseIs("BEAR_TREND");
        passUntilLeaves(trail, deal, "ACTIVE");
        trail.passUntil("отмена второй ноги принята", () -> nonNull(core
                .query("select close_reason from orders where external_id = ?", SECOND_ORDER).getFirst()
                .get("close_reason")));
        exchangeHoldsSecondLeg(trail, size, CANCELED);
        trail.passUntil("транши сделки терминальны", () -> {
            for (JsonNode tranche : dealRead(trail, deal).path("tranches")) {
                if (isFalse(Objects.equals("CLOSED", tranche.path("status").asString()))) {
                    return false;
                }
            }
            return true;
        });
        trail.relayCore();
        return deal;
    }

    /**
     * Пролог двух траншей: сделка по определению с двумя входными
     * объявлениями бычьей детали, вошли те транши, чей вход прошёл
     * преконтроль риска, их наливы и экспозиция наблюдены
     * (.claude/tests/cases/e2e-exit-and-close.md §«E2.3 — Под каскадом сделки
     * транш своей закрывающей ноги не выпускает», §«E2.6 — Транш с истинным
     * условием входа под сворачиванием заявки не выпускает», предусловия).
     *
     * <p><b>Второй транш удерживает его собственное действие, а не
     * сосед:</b> соседа, решённого тем же проходом, проверка одновременного
     * риска не видит (находка {@code F8} документа), и оба транша проходили
     * бы её вместе. Удержанный — номинал его ноги выше катастрофического
     * потолка сделки, а ноги соседа ниже: отказ временный и в карв-ауте
     * живого риска, строка исполнения остаётся живой, и транш стои́т в
     * предвходовой проверке с истинным условием.
     *
     * @param trail   тропа
     * @param entered сколько траншей входит — два либо один
     * @return идентичность сделки
     */
    static String walkToTwoTranches(Trail trail, Integer entered) {
        trail.exchangeAcceptsCommands();
        exchangeAcceptsTeardown(trail);
        trail.exchange().forgetScenarios();
        trail.exchange().answersPostTemplated(Trail.EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": [{"ordId": "okx-{{jsonPath request.body '$.clOrdId'}}",
                  "clOrdId": "{{jsonPath request.body '$.clOrdId'}}", "sCode": "0", "sMsg": "", "ts": "1758240000000"}]}
                """);
        trail.withoutDeals();
        trail.activeDefinition(twoTrancheDefinition(entered < 2));
        if (entered < 2) {
            simultaneousCeilingIs(trail, "1");
        }
        String deal = trail.openDeal();
        Database core = trail.database(Party.TRADING_CORE);
        trail.passUntil("входные ноги отправлены", () -> core
                .query("select id from orders where external_id is not null").size() == entered);
        trail.relayCore();
        String size = exchangeFillsEveryEntry(trail);
        exchangeMirrorsClose(trail);
        if (entered > 1) {
            exchangeMirrorsAttachedPair(trail);
        }
        trail.passUntil("наливы наблюдены", () -> core
                .query("select id from orders where external_status = 'filled'").size() == entered);
        trail.relayCore();
        exchangeHoldsPosition(trail, size);
        trail.passUntil("экспозиция вошедших траншей ненулевая", () -> {
            int exposed = 0;
            for (JsonNode tranche : dealRead(trail, deal).path("tranches")) {
                if (tranche.path("exposure").decimalValue().signum() > 0) {
                    exposed++;
                }
            }
            return Objects.equals(entered, exposed);
        });
        trail.relayCore();
        return deal;
    }

    /**
     * Глобальный потолок одновременного риска тенанта — поверхностью ядра;
     * прочие числа риск-аппетита — те же, что ставят общие предусловия.
     *
     * @param trail   тропа
     * @param percent потолок в процентах
     */
    static void simultaneousCeilingIs(Trail trail, String percent) {
        Answer answer = trail.call(Party.TRADING_CORE, "PUT", Trail.CORE + "/risk-appetites/" + trail.tenant(), null,
                """
                {
                  "globalSimultaneousRiskPerDealPercent": %s,
                  "globalCatastrophicRiskPerDealMultiplier": 100,
                  "globalConsecutiveLossLimit": 4
                }
                """.formatted(percent));
        if (answer.status() != 200) {
            throw new IllegalStateException("Предусловие не поставлено: потолок риска тенанта — "
                    + answer.status() + " " + answer.body());
        }
    }

    /**
     * Определение двух траншей: у бычьей детали эталона — второе входное
     * объявление, копия первого под своими ключами. Вход обоих — только по
     * индикаторам: правила «позиции нет» и «фаза бычья» сняты, чтобы условие
     * входа оставалось истинным и после налива соседа, и на медвежьей фазе,
     * которая уводит сделку в выход. Шаги основной защиты и её подстройки
     * сняты тоже: транш покрыт встроенной защитой входной ноги и уходит в
     * ведение сам, а отдельная защита второго транша дала бы у площадки
     * вторую условную заявку, которую кейсы группы не называют. Потолок
     * одновременного риска детали поднят до двух действий — иначе веер
     * детали отвергла бы валидация создания.
     *
     * <p><b>Удержание второго объявления — числа, а не правило:</b> стоп его
     * входа ближе, и размер ноги упирается уже в долю аллокации (47 контрактов
     * против 31 у соседа). Катастрофический множитель детали — 80: при
     * глобальном потолке тенанта 1%, который пролог ставит после активации,
     * потолок номинала сделки лежит между номиналами двух ног (8000 USDT
     * против 6200 и 9400 при средствах 10000). Создание такой потолок не
     * отвергает: валидация сверяет объявленный номинал детали с числами
     * тенанта на момент создания.
     *
     * @param secondHeldBack удержан ли вход второго объявления потолком номинала
     * @return тело определения
     */
    static String twoTrancheDefinition(Boolean secondHeldBack) {
        JsonNode definition = Json.tree(conditionOnlyExit());
        ObjectNode bull = (ObjectNode) bullDetail(definition);
        bull.put("strategySimultaneousRiskPerDealPercent", new BigDecimal("2.0"));
        ArrayNode tranches = (ArrayNode) bull.path("tranches");
        ObjectNode main = (ObjectNode) tranches.get(0);
        ObjectNode steps = (ObjectNode) main.path("stepsByStatus");
        steps.remove("ENTRY_FINALIZED");
        steps.remove("MANAGING");
        ObjectNode entry = (ObjectNode) steps.path("PRECHECK").get(0).path("condition");
        ArrayNode rules = entry.arrayNode();
        for (JsonNode rule : entry.path("rules")) {
            String type = rule.path("ruleType").asString();
            if (isFalse(Objects.equals("NO_OPEN_POSITION", type)) && isFalse(Objects.equals("MARKET_PHASE_IS", type))) {
                rules.add(((ObjectNode) rule).put("level", rules.size() + 1));
            }
        }
        entry.set("rules", rules);
        JsonNode second = Json.tree(main.toString().replace("bull_", "bull2_"));
        if (isTrue(secondHeldBack)) {
            bull.put("strategyCatastrophicRiskPerDealMultiplier", new BigDecimal("80"));
            ((ObjectNode) second.path("stepsByStatus").path("PRECHECK").get(0).path("actions").get(0)
                    .path("attachedProtection").path("stopLossSettings")).put("distancePercents", new BigDecimal("50"));
        }
        tranches.add(second);
        return definition.toString();
    }

    /**
     * Площадка отдаёт каждую отправленную входную ногу налитой целиком — по её
     * клиентскому идентификатору, — а встроенные защиты всех ног —
     * материализованными живыми.
     *
     * @param trail тропа
     * @return суммарный размер налива в контрактах — размер позиции
     */
    static String exchangeFillsEveryEntry(Trail trail) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> order : trail.database(Party.TRADING_CORE)
                .query("select internal_id, external_id, size from orders where external_id is not null order by id")) {
            trail.exchange().answersWhere(Trail.EXCHANGE_ORDER, "clOrdId", String.valueOf(order.get("internal_id")),
                    """
                    {"code": "0", "msg": "", "data": [{"instId": "%s", "ordId": "%s", "clOrdId": "%s",
                      "ordType": "market", "side": "buy", "posSide": "net", "state": "filled", "px": "",
                      "sz": "%s", "accFillSz": "%s", "avgPx": "%s", "fee": "-0.1", "feeCcy": "USDT",
                      "cTime": "1758240000000", "uTime": "1758240001000"}]}
                    """.formatted(Trail.EXTERNAL_INSTRUMENT, order.get("external_id"), order.get("internal_id"),
                    plain(order.get("size")), plain(order.get("size")), Trail.ENTRY_PRICE));
            total = total.add((BigDecimal) order.get("size"));
        }
        trail.exchange().answers(Trail.EXCHANGE_ALGO_PENDING, materialized(attachedProtections(trail)));
        return plain(total);
    }

    /**
     * Определение пролога с добором: эталон с шагом выхода «только условие»,
     * у транша бычьей детали которого в ведении стои́т шаг второго входа — та
     * же входная заявка со встроенной защитой, условие «позиция открыта».
     *
     * <p><b>Потолок одновременного риска детали поднят до двух действий:</b> у
     * эталона он равен риску одного действия, и добор отвергался бы
     * преконтролем риска.
     *
     * @return тело определения
     */
    static String scaleInDefinition() {
        JsonNode definition = Json.tree(conditionOnlyExit());
        JsonNode bull = bullDetail(definition);
        ((ObjectNode) bull).put("strategySimultaneousRiskPerDealPercent", new BigDecimal("2.0"));
        JsonNode tranche = bull.path("tranches").get(0);
        ObjectNode scaleIn = (ObjectNode) Json.tree(tranche.path("stepsByStatus").path("PRECHECK").get(0).toString());
        ((ArrayNode) scaleIn.path("condition").path("rules")).removeAll()
                .add(Json.tree("{\"level\": 1, \"ruleType\": \"POSITION_OPENED\"}"));
        ((ObjectNode) scaleIn.path("actions").get(0)).put("key", "bull_scale_in");
        ((ArrayNode) tranche.path("stepsByStatus").path("MANAGING")).add(scaleIn);
        return definition.toString();
    }

    /** Бычья деталь определения. */
    static JsonNode bullDetail(JsonNode definition) {
        for (JsonNode detail : definition.path("details")) {
            if (Objects.equals("BULL_TREND", detail.path("marketPhaseType").asString())) {
                return detail;
            }
        }
        throw new IllegalStateException("У эталона нет бычьей детали");
    }

    /**
     * Площадка принимает отдельную защиту транша и отвечает на её чтение
     * живой, а снятие отдельной и встроенной защит принимает и отражает на
     * чтениях: отдельная читается снятой, встроенная уходит из живых и
     * находится в истории снятых.
     *
     * @param trail тропа
     * @param size  размер защиты в контрактах
     */
    static void exchangeMirrorsProtection(Trail trail, String size) {
        Stub exchange = trail.exchange();
        exchange.answersPostTemplated(ORDER_ALGO, """
                {"code": "0", "msg": "", "data": [{"algoId": "%s",
                  "algoClOrdId": "{{jsonPath request.body '$.algoClOrdId'}}", "sCode": "0", "sMsg": ""}]}
                """.formatted(EXTERNAL_OCO));
        String oco = """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "algoId": "%s",
                  "algoClOrdId": "{{request.query.algoClOrdId}}", "state": "%s", "sz": "%s",
                  "slTriggerPx": "1970", "slTriggerPxType": "mark", "tpTriggerPx": "2060", "tpTriggerPxType": "mark",
                  "cTime": "1758240000000", "uTime": "1758240001000"}]}
                """;
        exchange.answersTemplated(ORDER_ALGO, oco.formatted(Trail.EXTERNAL_INSTRUMENT, EXTERNAL_OCO, "live", size));
        exchange.answersInState(ORDER_ALGO, OCO_SCENARIO, CANCELED,
                oco.formatted(Trail.EXTERNAL_INSTRUMENT, EXTERNAL_OCO, CANCELED, size));
        exchange.answersPostMoving(CANCEL_ALGOS, "$[0].algoId", EXTERNAL_OCO, OCO_SCENARIO, Stub.STARTED, CANCELED,
                """
                {"code": "0", "msg": "", "data": [{"algoId": "%s", "sCode": "0", "sMsg": ""}]}
                """.formatted(EXTERNAL_OCO));
        List<Map<String, Object>> attached = attachedProtections(trail);
        String attachedId = String.valueOf(attached.getFirst().get("internal_id"));
        exchange.answersPostMoving(CANCEL_ALGOS, "$[0].algoClOrdId", attachedId, ATTACHED_SCENARIO, Stub.STARTED,
                CANCELED, algoCancelAck(attachedId));
        exchange.answersInState(Trail.EXCHANGE_ALGO_PENDING, ATTACHED_SCENARIO, CANCELED, materialized(List.of()));
        exchangeKeepsCanceledHistory(trail, attached);
    }

    /**
     * Площадка держит встроенные защиты двух траншей живыми и снятие каждой
     * отражает на чтениях: снятая уходит из живых, соседняя остаётся, и обе
     * находятся в истории снятых.
     *
     * <p><b>Состояний у пары четыре</b> — ни одна не снята, снята первая,
     * снята вторая, сняты обе, — и порядок снятия площадка не предписывает:
     * его выбирает ядро по порядку траншей, а он произволен (ловушка TC-194
     * скилла кода тестов).
     *
     * @param trail тропа
     */
    static void exchangeMirrorsAttachedPair(Trail trail) {
        Stub exchange = trail.exchange();
        List<Map<String, Object>> attached = attachedProtections(trail);
        Map<String, Object> first = attached.get(0);
        Map<String, Object> second = attached.get(1);
        String firstId = String.valueOf(first.get("internal_id"));
        String secondId = String.valueOf(second.get("internal_id"));
        exchange.answersPostMoving(CANCEL_ALGOS, "$[0].algoClOrdId", firstId, ATTACHED_SCENARIO, Stub.STARTED,
                FIRST, algoCancelAck(firstId));
        exchange.answersPostMoving(CANCEL_ALGOS, "$[0].algoClOrdId", firstId, ATTACHED_SCENARIO, SECOND,
                BOTH, algoCancelAck(firstId));
        exchange.answersPostMoving(CANCEL_ALGOS, "$[0].algoClOrdId", secondId, ATTACHED_SCENARIO, Stub.STARTED,
                SECOND, algoCancelAck(secondId));
        exchange.answersPostMoving(CANCEL_ALGOS, "$[0].algoClOrdId", secondId, ATTACHED_SCENARIO, FIRST,
                BOTH, algoCancelAck(secondId));
        exchange.answers(Trail.EXCHANGE_ALGO_PENDING, materialized(attached));
        exchange.answersInState(Trail.EXCHANGE_ALGO_PENDING, ATTACHED_SCENARIO, FIRST, materialized(List.of(second)));
        exchange.answersInState(Trail.EXCHANGE_ALGO_PENDING, ATTACHED_SCENARIO, SECOND, materialized(List.of(first)));
        exchange.answersInState(Trail.EXCHANGE_ALGO_PENDING, ATTACHED_SCENARIO, BOTH, materialized(List.of()));
        exchangeKeepsCanceledHistory(trail, attached);
    }

    /**
     * Площадка принимает закрытие позиции и отражает его на чтениях: позиция
     * читается плоской, а её эпизод — записью закрытия с той же парой
     * «идентификатор, момент открытия».
     *
     * @param trail тропа
     */
    static void exchangeMirrorsClose(Trail trail) {
        Stub exchange = trail.exchange();
        exchange.answersPostMoving(CLOSE_POSITION, "$.instId", Trail.EXTERNAL_INSTRUMENT, POSITION_SCENARIO,
                Stub.STARTED, FLAT, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "posSide": "net", "clOrdId": "", "tag": "",
                  "sCode": "0", "sMsg": ""}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT));
        exchange.answersInState(Trail.EXCHANGE_POSITIONS, POSITION_SCENARIO, FLAT, """
                {"code": "0", "msg": "", "data": []}
                """);
        exchange.answersInState(POSITIONS_HISTORY, POSITION_SCENARIO, FLAT, """
                {"code": "0", "msg": "", "data": [{"posId": "%s", "instId": "%s", "direction": "long",
                  "realizedPnl": "-0.2", "ccy": "USDT", "closeAvgPx": "%s", "pnl": "0", "fee": "-0.2",
                  "fundingFee": "0", "liqPenalty": "0", "type": "2",
                  "cTime": "1758240000000", "uTime": "1758240005000"}]}
                """.formatted(EXTERNAL_POSITION, Trail.EXTERNAL_INSTRUMENT, Trail.ENTRY_PRICE));
    }

    /**
     * Площадка отдаёт на чтение истории закрытых эпизодов названные записи —
     * с того момента, как позиция стала плоской: принятое закрытие сценарий
     * переводит, и ответ, заведённый позже, перекрывает прежний.
     *
     * @param trail   тропа
     * @param records записи закрытия через запятую; пусто — истории нет
     */
    static void exchangeKeepsCloseRecords(Trail trail, String records) {
        trail.exchange().answersInState(POSITIONS_HISTORY, POSITION_SCENARIO, FLAT, """
                {"code": "0", "msg": "", "data": [%s]}
                """.formatted(records));
    }

    /**
     * Запись закрытия эпизода позиции тропы у площадки.
     *
     * @param createdAt   биржевое время открытия эпизода, мс
     * @param modifiedAt  биржевое время закрытия эпизода, мс
     * @param pnl         результат без издержек
     * @param fee         комиссия — знаковая, как у площадки
     * @param realizedPnl готовый net эпизода
     * @return запись ответа площадки
     */
    static String closeRecord(Long createdAt, Long modifiedAt, String pnl, String fee, String realizedPnl) {
        return """
                {"posId": "%s", "instId": "%s", "direction": "long", "realizedPnl": "%s", "ccy": "USDT",
                  "closeAvgPx": "%s", "pnl": "%s", "fee": "%s", "fundingFee": "0", "liqPenalty": "0", "type": "2",
                  "cTime": "%d", "uTime": "%d"}
                """.formatted(EXTERNAL_POSITION, Trail.EXTERNAL_INSTRUMENT, realizedPnl, Trail.ENTRY_PRICE, pnl, fee,
                createdAt, modifiedAt);
    }

    /**
     * Площадка отдаёт движения средств одной страницей свежего эндпоинта:
     * страница за последней записью пуста, архив пуст. Время площадки —
     * названный момент.
     *
     * @param trail      тропа
     * @param sourceTime время площадки, мс
     * @param lastBillId идентификатор последней записи страницы — якорь следующей
     * @param bills      записи движений через запятую
     */
    static void exchangeKeepsBills(Trail trail, Long sourceTime, String lastBillId, String bills) {
        Stub exchange = trail.exchange();
        exchange.answers(Trail.EXCHANGE_TIME, """
                {"code": "0", "msg": "", "data": [{"ts": "%d"}]}
                """.formatted(sourceTime));
        exchange.answers(BILLS, """
                {"code": "0", "msg": "", "data": [%s]}
                """.formatted(bills));
        exchange.answersWhere(BILLS, "after", lastBillId, """
                {"code": "0", "msg": "", "data": []}
                """);
        exchange.answers(BILLS_ARCHIVE, """
                {"code": "0", "msg": "", "data": []}
                """);
    }

    /**
     * Запись движения средств у площадки.
     *
     * @param billId     идентификатор записи
     * @param instrument инструмент движения
     * @param type       тип записи
     * @param subType    подтип записи
     * @param ccy        валюта движения
     * @param amount     изменение баланса
     * @param fee        комиссионная компонента
     * @param at         время события, мс
     * @return запись ответа площадки
     */
    static String bill(String billId, String instrument, String type, String subType, String ccy, String amount,
                       String fee, Long at) {
        return """
                {"billId": "%s", "instId": "%s", "type": "%s", "subType": "%s", "ccy": "%s", "balChg": "%s",
                  "posBalChg": "0", "fee": "%s", "ordId": "", "ts": "%d"}
                """.formatted(billId, instrument, type, subType, ccy, amount, fee, at);
    }

    /**
     * Площадка отвечает на секундную свечу индекса названной ценой закрытия
     * — свечой, открытой в секунду события, — а на минутную пусто.
     *
     * @param trail  тропа
     * @param openAt момент открытия свечи, мс
     * @param close  цена закрытия; пусто — секундной свечи нет
     */
    static void exchangeQuotesIndex(Trail trail, Long openAt, String close) {
        String empty = """
                {"code": "0", "msg": "", "data": []}
                """;
        trail.exchange().answers(INDEX_CANDLES, empty);
        trail.exchange().answersWhere(INDEX_CANDLES, "bar", "1s", isNull(close) ? empty : """
                {"code": "0", "msg": "", "data": [["%d", "%s", "%s", "%s", "%s", "1"]]}
                """.formatted(openAt, close, close, close, close));
    }

    /**
     * История снятых защит площадки: названные встроенные защиты — на ноге
     * снятых, прочие ноги пусты.
     */
    private static void exchangeKeepsCanceledHistory(Trail trail, List<Map<String, Object>> attached) {
        trail.exchange().answers(ALGO_HISTORY, """
                {"code": "0", "msg": "", "data": []}
                """);
        trail.exchange().answersWhere(ALGO_HISTORY, "state", CANCELED, """
                {"code": "0", "msg": "", "data": [%s]}
                """.formatted(protectionRecords(attached, CANCELED)));
    }

    /** Живые материализованные встроенные защиты — ответом площадки. */
    private static String materialized(List<Map<String, Object>> attached) {
        return """
                {"code": "0", "msg": "", "data": [%s]}
                """.formatted(protectionRecords(attached, "live"));
    }

    /** Записи встроенных защит у площадки: клиентский идентификатор — наш, биржевой — её. */
    private static String protectionRecords(List<Map<String, Object>> attached, String state) {
        return String.join(", ", attached.stream()
                .map(protection -> """
                        {"instId": "%s", "algoId": "okx-%s", "algoClOrdId": "%s", "ordType": "conditional",
                          "side": "sell", "posSide": "net", "state": "%s", "sz": "%s", "slTriggerPx": "%s",
                          "slTriggerPxType": "mark", "slOrdPx": "-1", "cTime": "1758240000000",
                          "uTime": "1758240002000"}
                        """.formatted(Trail.EXTERNAL_INSTRUMENT, protection.get("internal_id"),
                        protection.get("internal_id"), state, plain(protection.get("size")),
                        plain(protection.get("stop_loss_trigger_price"))))
                .toList());
    }

    private static String algoCancelAck(String algoClOrdId) {
        return """
                {"code": "0", "msg": "", "data": [{"algoId": "okx-%s", "algoClOrdId": "%s", "sCode": "0", "sMsg": ""}]}
                """.formatted(algoClOrdId, algoClOrdId);
    }

    /** Встроенные защиты ядра — в порядке заведения. */
    private static List<Map<String, Object>> attachedProtections(Trail trail) {
        return trail.database(Party.TRADING_CORE)
                .query("select internal_id, size, stop_loss_trigger_price from attached_algo_orders order by id");
    }

    /**
     * Площадка ставит заявку добора под своим идентификатором и отвечает на
     * её чтение названным состоянием без налива; клиентский идентификатор —
     * эхом запроса.
     *
     * @param trail тропа
     * @param size  размер ноги в контрактах
     * @param state состояние заявки у площадки
     */
    static void exchangeHoldsSecondLeg(Trail trail, String size, String state) {
        trail.exchange().answersPostTemplated(Trail.EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": [{"ordId": "%s", "clOrdId": "{{jsonPath request.body '$.clOrdId'}}",
                  "sCode": "0", "sMsg": "", "ts": "1758240000000"}]}
                """.formatted(SECOND_ORDER));
        trail.exchange().answersWhere(Trail.EXCHANGE_ORDER, "ordId", SECOND_ORDER, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "ordId": "%s",
                  "clOrdId": "{{request.query.clOrdId}}", "ordType": "market", "side": "buy", "posSide": "net",
                  "state": "%s", "px": "", "sz": "%s", "accFillSz": "0", "avgPx": "", "fee": "0", "feeCcy": "USDT",
                  "cTime": "1758240100000", "uTime": "1758240101000"}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT, SECOND_ORDER, state, size));
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
