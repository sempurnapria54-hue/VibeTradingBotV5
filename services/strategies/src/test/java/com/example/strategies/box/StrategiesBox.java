package com.example.strategies.box;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `strategies`: реальный контекст сервиса со ВСЕМИ его
 * бинами, наблюдаемый только снаружи — своей поверхностью, базой, стабом
 * единственного соседа и брокером
 * (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний
 * бин не подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по
 * адресу случайного порта, а не {@code MockMvc} и не автовайренный
 * {@code OutboxRelayJob}. Наблюдатели субстрата ({@link Rows},
 * {@link PeerStub}) ходят своим соединением — ящик смотрит на субстрат
 * снаружи.
 *
 * <p><b>ВЫХОД у этого предмета чаще всего есть СТРОКА OUTBOX, а не
 * ответ.</b> Ответ поверхности говорит только, что переход принят; факт,
 * ради которого переход существует, лежит строкой в своей базе и уезжает
 * тиком реле (.claude/tests/cases/strategies.md §«Новая ось формы —
 * событие как ВЫХОД»). Поэтому клетка утверждает об ОБЕИХ половинах, а
 * отсутствие события — такой же выход, и здесь он частый.
 *
 * <p><b>Тенант приходит ЗАГОЛОВКОМ, и он есть операнд вызова.</b> Все три
 * точки определения требуют его непустым; клетки о его отсутствии и
 * пустоте подают заголовок сами
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p><b>Свойств контекста здесь не объявлено ни одного, и это несущее.</b>
 * Перечень свойств задаёт КАЖДЫЙ класс кейсов своим методом
 * {@code @DynamicPropertySource}: положение осей конфигурации есть ВХОД
 * ящика, а унаследованный метод реестра перекрывал бы свои
 * переопределения в порядке, которого контракт реестра не обещает.
 * Цена названа — контекст на класс конфигурации, а не один на прогон;
 * классы, которым довольно штатного положения осей, берут его у
 * {@link SharedStrategiesBox} и делят один контекст.
 *
 * <p><b>База опустошается перед каждой клеткой.</b> Вход клетки есть её
 * собственное состояние определений и строк outbox, и наследство соседки
 * сделало бы отрицания («строки события нет», «перечень пуст»)
 * утверждениями о прогоне, а не о клетке.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class StrategiesBox {

    /** Поверхность определений: корень всех трёх точек. */
    protected static final String STRATEGIES = "/api/v1/strategies";

    /** Ручной тик реле outbox. */
    protected static final String RELAY_TICK = STRATEGIES + "/jobs/outbox-relay";

    /**
     * Потолок ожидания следа асинхронного тика реле.
     *
     * <p>Ответ фасада уходит раньше работы, и след наблюдается отметкой
     * публикации; ожидание ограничено, потому что бесконечное висело бы
     * ровно там, где реле не сработало.
     */
    protected static final Duration RELAY_WAIT = Duration.ofSeconds(60);

    /** Запись фасада о конце прохода: ею наблюдается конец асинхронного тика. */
    protected static final String RELAY_FINISHED = "Manual OutboxRelayJob trigger finished";

    /** Проба живости: единственная открытая точка контура доступа. */
    protected static final String HEALTH = "/actuator/health";

    /** Проверка пары «счёт × инструмент» у соседа. */
    protected static final String PEER_PAIR_CHECKS = "/api/v1/trading-core/pair-checks";

    /** Числа риск-аппетита тенанта у соседа: путь несёт идентичность тенанта. */
    protected static final String PEER_RISK_APPETITES = "/api/v1/trading-core/risk-appetites";

    /** Тенант, которым ходит большинство кейсов. */
    protected static final String TENANT = "T1";

    /** Второй тенант: им наблюдается радиус чтения и перехода. */
    protected static final String SECOND_TENANT = "T2";

    /** Заголовок контекста тенанта: тот же, что читает поверхность. */
    protected static final String TENANT_HEADER = "X-Tenant-Id";

    /** Таблица определений: её строки читает прямой ассерт по колонкам. */
    protected static final String STRATEGIES_TABLE = "strategies";

    /** Таблица неопубликованных фактов: отсутствие строки — такой же выход. */
    protected static final String OUTBOX_TABLE = "outbox_events";

    /** Класс события активации: единственный, что везёт снимок дерева. */
    protected static final String ACTIVATED = "STRATEGY_ACTIVATED";

    /** Класс события деактивации. */
    protected static final String DEACTIVATED = "STRATEGY_DEACTIVATED";

    /** Класс события удаления. */
    protected static final String DELETED = "STRATEGY_DELETED";

    /**
     * Потолок одновременного риска тенанта штатного прогона.
     *
     * <p>Совпадает с объявленным эталоном: годное определение обязано
     * проходить охрану создания, и число, назначенное «с запасом»,
     * отняло бы предмет у клеток о неравенствах.
     */
    protected static final String GLOBAL_SIMULTANEOUS_PERCENT = "1.0";

    /** Предел множителя катастрофического потолка тенанта штатного прогона. */
    protected static final String GLOBAL_CATASTROPHIC_MULTIPLIER = "100.0";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /** Наблюдатель строк базы субстрата. */
    protected final Rows rows = Rows.shared();

    /** Стаб и наблюдатель поверхности торгового ядра. */
    protected final PeerStub peer = PeerStub.tradingCore();

    /** Стаб провайдера идентичности: им подписываются токены кейсов. */
    protected final IdentityStub identity = IdentityStub.stub();

    /** Перед каждой клеткой: база пуста, стаб забыл и заготовки, и записи. */
    @BeforeEach
    void resetSubstrate() {
        rows.clear();
        peer.reset();
    }

    /**
     * Ставит штатные ответы соседа: ссылки разрешаются, числа назначены.
     *
     * <p><b>Заготовки, а не предусловие в базе:</b> операнды проверки
     * живут у соседа по построению, и подставленные в свою базу они
     * говорили бы о нашей вставке, а не о поведении сервиса.
     */
    protected void peerResolvesEverything() {
        peer.answers(PEER_PAIR_CHECKS, Feed.resolvingPairCheck());
        peer.answers(PEER_RISK_APPETITES + "/" + TENANT,
                Feed.riskAppetite(TENANT, GLOBAL_SIMULTANEOUS_PERCENT, GLOBAL_CATASTROPHIC_MULTIPLIER));
        peer.answers(PEER_RISK_APPETITES + "/" + SECOND_TENANT,
                Feed.riskAppetite(SECOND_TENANT, GLOBAL_SIMULTANEOUS_PERCENT, GLOBAL_CATASTROPHIC_MULTIPLIER));
    }

    /**
     * Заводит определение ТРОПОЙ ЯЩИКА — созданием через поверхность — и
     * отдаёт его идентичность.
     *
     * <p><b>Поверхностью, а не вставкой дерева в базу:</b> строки дерева
     * производит сам сервис, и вставка опиралась бы на строку, которой он
     * не производил.
     *
     * <p><b>Записи стаба после этого забываются.</b> Чтения предусловия
     * ушли в тот же журнал, что и чтения клетки, и без разделения
     * отрицание «запросов нет» не сошлось бы никогда.
     *
     * @param tenantInternalId тенант-владелец заводимого определения
     * @return идентичность заведённого определения
     */
    protected String given(String tenantInternalId) {
        return given(tenantInternalId, Bodies.reference());
    }

    /**
     * То же названным телом: вход клеток, у которых предмет — радиус
     * инварианта, а не само определение.
     *
     * @param tenantInternalId тенант-владелец заводимого определения
     * @param body             тело команды создания
     * @return идентичность заведённого определения
     */
    protected String given(String tenantInternalId, String body) {
        Answer answer = post(STRATEGIES, tenantInternalId, body);
        if (answer.status() != 201) {
            throw new AssertionError("Предусловие не поставлено: создание ответило "
                    + answer.status() + ": " + answer.body());
        }
        peer.forgetRequests();
        return String.valueOf(answer.asObject().get("internalId"));
    }

    /**
     * Переводит определение в целевой статус — единственной тропой
     * жизненного цикла.
     *
     * <p><b>Целевой статус подаётся СТРОКОЙ, а не значением перечня:</b>
     * часть клеток называет то, чего в перечне нет вовсе, и типизованная
     * подача такой вход выразить не даёт.
     *
     * @param internalId       идентичность определения
     * @param tenantInternalId тенант заголовка контекста
     * @param target           целевой статус дословно
     */
    protected Answer moveTo(String internalId, String tenantInternalId, String target) {
        return put(STRATEGIES + "/" + internalId + "/status", tenantInternalId,
                "{\"status\": \"" + target + "\"}");
    }

    /**
     * Заводит определение и переводит его в {@code ACTIVE} — тропой
     * ящика, а не вставкой строки.
     *
     * <p><b>Записи стаба после этого забываются</b> по той же причине, что
     * и у {@link #given(String)}: чтения предусловия ушли в тот же журнал,
     * что и чтения клетки.
     *
     * @param tenantInternalId тенант-владелец
     * @return идентичность активированного определения
     */
    protected String givenActive(String tenantInternalId) {
        String internalId = given(tenantInternalId);
        Answer moved = moveTo(internalId, tenantInternalId, "ACTIVE");
        if (moved.status() != 200) {
            throw new AssertionError("Предусловие не поставлено: активация ответила "
                    + moved.status() + ": " + moved.body());
        }
        peer.forgetRequests();
        return internalId;
    }

    /** Статус определения, прочитанный поверхностью его тенанта. */
    protected String statusOf(String internalId, String tenantInternalId) {
        return String.valueOf(get(STRATEGIES + "/" + internalId, tenantInternalId)
                .asObject().get("status"));
    }

    /** Строки outbox в порядке записи. */
    protected List<Map<String, Object>> events() {
        return rows.all(OUTBOX_TABLE);
    }

    /** Строки outbox названного класса, в порядке записи. */
    protected List<Map<String, Object>> events(String eventType) {
        return events().stream()
                .filter(row -> Objects.equals(eventType, row.get("event_type")))
                .toList();
    }

    /** Единственная строка outbox названного класса; иное число — падение. */
    protected Map<String, Object> event(String eventType) {
        List<Map<String, Object>> found = events(eventType);
        if (found.size() != 1) {
            throw new AssertionError("Ожидалась ровно одна строка класса " + eventType
                    + ", в outbox их " + found.size() + ": " + events().stream()
                    .map(row -> row.get("event_type")).toList());
        }
        return found.getFirst();
    }

    /**
     * Содержимое строки outbox дословно.
     *
     * <p><b>Дословно, а не разобранным:</b> клетки утверждают об
     * ОТСУТСТВИИ полей — ключа базы в снимке, поля дерева у прочих
     * переходов, — и разбор такие ожидания выразить не даёт.
     */
    protected static String contentTextOf(Map<String, Object> outboxRow) {
        return String.valueOf(outboxRow.get("payload"));
    }

    /**
     * Содержимое строки outbox как объект.
     *
     * <p>Разбирается тем же парсером, что и тело ответа: форма у них одна
     * — документ JSON, — и второй разборщик разошёлся бы с первым.
     */
    protected static Map<String, Object> contentOf(Map<String, Object> outboxRow) {
        return JsonParserFactory.getJsonParser().parseMap(contentTextOf(outboxRow));
    }

    /** Ручной тик реле outbox: единственная джоба сервиса. */
    protected Answer tickRelay() {
        return post(RELAY_TICK, TENANT, "");
    }

    /**
     * Ручной тик реле, дождавшийся СВОЕГО конца.
     *
     * <p><b>Конец прохода ждётся записью фасада в журнале, а не
     * паузой.</b> Фасад асинхронен — ответ уходит раньше работы, — и у
     * прохода, который ничего не произвёл (пустой outbox, выключенное
     * реле), наблюдаемого следа нет вовсе: ни записи в теме, ни отметки в
     * базе. Без записи журнала отрицание «в тему не ушло ничего» мерило
     * бы скорость теста, а не поведение сервиса, а {@code Thread.sleep}
     * платил бы временем всегда и не гарантировал бы ничего.
     *
     * @param atMost потолок ожидания конца прохода
     * @return ответ фасада на запуск
     */
    protected Answer relayPass(Duration atMost) {
        Integer mark = AppLog.mark();
        Answer answer = tickRelay();
        Awaitility.await()
                .atMost(atMost)
                .pollInterval(Duration.ofMillis(200))
                .until(() -> AppLog.since(mark).contains(RELAY_FINISHED));
        return answer;
    }

    /** Ручной тик реле, дождавшийся своего конца штатным потолком. */
    protected Answer relayPass() {
        return relayPass(RELAY_WAIT);
    }

    /**
     * Ждёт, пока реле пометит названное число строк опубликованными.
     *
     * <p><b>Ждётся отметка в базе, а не запись в теме, и это не
     * равнозначные ожидания.</b> Отметка ставится ПОСЛЕ подтверждения
     * брокером, поэтому дождавшийся её кейс знает: запись уже лежит.
     * Обратное неверно — по записи в теме нельзя заключить, что отметка
     * поставлена.
     *
     * <p>Ответ фасада асинхронен, поэтому ожидание ограничено потолком:
     * «не упало» проверкой не является.
     *
     * @param published сколько строк обязано оказаться помеченными
     */
    protected void awaitPublished(Integer published) {
        Awaitility.await()
                .atMost(RELAY_WAIT)
                .pollInterval(Duration.ofMillis(200))
                .until(() -> marked().size() == published);
    }

    /** Идентичности событий, помеченных опубликованными. */
    protected List<String> marked() {
        return events().stream()
                .filter(row -> Objects.nonNull(row.get("published_at")))
                .map(row -> String.valueOf(row.get("event_id")))
                .toList();
    }

    /** Чтение под сервисным токеном и контекстом названного тенанта. */
    protected Answer get(String path, String tenantInternalId) {
        return send(authorized(request(path)).header(TENANT_HEADER, tenantInternalId).GET());
    }

    /**
     * Чтение БЕЗ предъявленной идентичности: заголовок контекста есть,
     * заголовка идентичности нет.
     *
     * <p>Контекст подаётся намеренно: клетка о закрытом умолчании
     * утверждает, что отвергает контур, а не разбор контекста.
     */
    protected Answer getAnonymously(String path) {
        return send(request(path).header(TENANT_HEADER, TENANT).GET());
    }

    /** Чтение под НАЗВАННЫМ токеном: вход клеток о негодных осях токена. */
    protected Answer getWith(String path, String token) {
        return send(request(path)
                .header("Authorization", "Bearer " + token)
                .header(TENANT_HEADER, TENANT)
                .GET());
    }

    /** Чтение под сервисным токеном БЕЗ заголовка контекста. */
    protected Answer getWithoutTenant(String path) {
        return send(authorized(request(path)).GET());
    }

    /** Мутирующий вызов под сервисным токеном и контекстом названного тенанта. */
    protected Answer post(String path, String tenantInternalId, String body) {
        return send(authorized(request(path))
                .header(TENANT_HEADER, tenantInternalId)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Мутирующий вызов БЕЗ заголовка контекста. */
    protected Answer postWithoutTenant(String path, String body) {
        return send(authorized(request(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Смена статуса под сервисным токеном и контекстом названного тенанта. */
    protected Answer put(String path, String tenantInternalId, String body) {
        return send(authorized(request(path))
                .header(TENANT_HEADER, tenantInternalId)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60));
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder request) {
        return request.header("Authorization", "Bearer " + identity.serviceToken());
    }

    private static Answer send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> answer = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(answer.statusCode(), answer.body(), answer.headers().map());
        } catch (IOException failure) {
            throw new IllegalStateException("Поверхность ящика не ответила", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа поверхности прервано", failure);
        }
    }

    /**
     * Ответ поверхности: код и тело дословно.
     *
     * <p><b>Запись, а не разобранный объект:</b> часть кейсов утверждает о
     * ТЕЛЕ — что в нём нет числового ключа базы, что деталей в перечне
     * нет, что причина отказа неразличима, — и разбор в типизованную форму
     * такие ожидания выразить не даёт.
     *
     * @param status  код ответа
     * @param body    тело ответа дословно
     * @param headers заголовки ответа по именам
     */
    protected record Answer(Integer status, String body, Map<String, List<String>> headers) {

        /**
         * Первое значение заголовка ответа; пусто — заголовка не было.
         *
         * <p>Имя ищется БЕЗ учёта регистра: регистр имени заголовка
         * контракта не несёт, и ассерт, чувствительный к нему, мерил бы
         * написание, а не наличие.
         *
         * @param name имя заголовка
         */
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }

        /** Тело как объект. */
        Map<String, Object> asObject() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }

        /** Тело как перечень объектов. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> asList() {
            return JsonParserFactory.getJsonParser().parseList(body).stream()
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }

        /** Единственный объект перечня. */
        Map<String, Object> single() {
            List<Map<String, Object>> items = asList();
            if (items.size() != 1) {
                throw new AssertionError("Ожидался ровно один объект, пришло " + items.size() + ": " + body);
            }
            return items.getFirst();
        }

        /** Вложенный перечень тела по имени поля; пустой, когда его нет. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nestedList(String field) {
            Object value = asObject().get(field);
            return Objects.isNull(value) ? List.of() : (List<Map<String, Object>>) value;
        }

        /**
         * Несёт ли тело единый error-DTO поверхности: класс отказа и
         * момент.
         *
         * <p>Форма читается по ПОЛЯМ, а не по коду ответа: контейнерный
         * отказ отдаёт своё тело с тем же кодом.
         */
        Boolean carriesErrorDto() {
            if (Objects.isNull(body) || body.isBlank()) {
                return Boolean.FALSE;
            }
            // Тело, которое объектом не является, формы не несёт — и это
            // ОТВЕТ клетки, а не её отказ: иначе красная клетка падала бы
            // разбором и переставала называть, чем ожидание не сошлось.
            try {
                Map<String, Object> parsed = asObject();
                return parsed.containsKey("code") && parsed.containsKey("occurredAt");
            } catch (RuntimeException notAnObject) {
                return Boolean.FALSE;
            }
        }

        /** Класс отказа единого error-DTO. */
        String errorCode() {
            return String.valueOf(asObject().get("code"));
        }

        /** Пояснение отказа: в нём едет реджект-код причины. */
        String errorMessage() {
            return String.valueOf(asObject().get("message"));
        }
    }
}
