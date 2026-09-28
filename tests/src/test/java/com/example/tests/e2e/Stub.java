package com.example.tests.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

/**
 * Стаб чужой поверхности — соседа, который стороной тропы не является:
 * площадки, {@code auth}, {@code market-data}
 * (.claude/skills/test-code.md §«Уровень 3 — сквозной набор»).
 *
 * <p><b>HTTP/2 без шифрования выключен:</b> клиент стороны пытается
 * апгрейд, и вызов с телом рвётся на стабе (ловушка TC-050 скилла кода
 * тестов).
 *
 * <p><b>Журнал обращений отдаётся в порядке прихода</b>, а не от свежего к
 * старому, как его держит WireMock: кейсы о порядке читают его как ход
 * событий.
 *
 * <p><b>Область — заголовок, которым сосед называет, ЧЕЙ это запрос</b>
 * (у площадки — ключ счёта {@code OK-ACCESS-KEY}). Поставленная, она
 * сужает всё, что стаб делает дальше: ответ отдаётся только запросу с этим
 * значением заголовка, сценарий живёт под её именем, журнал и забывание
 * сценариев — в её пределах. Ответы прежней области остаются и отвечают
 * своим запросам: сделка прежнего счёта на общем стенде видит свою площадку,
 * а не площадку кейса. <b>Общий путь</b> области не знает: запрос без
 * подписи счёта ни одному счёту не адресован, и в журнал области он входит.
 */
public final class Stub {

    /** Начальное состояние всякого сценария площадки. */
    public static final String STARTED = Scenario.STARTED;

    private static final String ANSWERING = "answering";

    private static final String TEMPLATE = "response-template";

    private static final Integer IN_STATE = 1;

    private static final Integer WHERE = 2;

    private static final Integer WITHOUT = 3;

    private final String name;
    private final WireMockServer server;
    private final String host;
    private final Set<String> sharedPaths = new HashSet<>();
    private String scopeHeader;
    private String scopeValue;

    public Stub(String name) {
        this.name = name;
        this.host = "localhost";
        this.server = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .http2PlainDisabled(true));
        this.server.start();
    }

    /**
     * Стаб на адресе конвенции кластера — своём loopback-адресе и общем порту
     * владельцев: так его находит периметр, собирающий адрес владельца
     * шаблоном из имени.
     *
     * @param name    имя соседа
     * @param address loopback-адрес стаба
     * @param port    общий порт владельцев
     */
    public Stub(String name, String address, Integer port) {
        this.name = name;
        this.host = address;
        this.server = new WireMockServer(WireMockConfiguration.options()
                .bindAddress(address)
                .port(port)
                .http2PlainDisabled(true));
        this.server.start();
    }

    /** Адрес стаба, едущий стороне ключом её соседа. */
    public String baseUrl() {
        return "http://" + host + ":" + server.port();
    }

    /** Имя соседа, которого стаб играет. */
    public String name() {
        return name;
    }

    /**
     * Ставит область: дальнейшие ответы, сценарии и чтения журнала — только
     * для запросов с этим значением заголовка.
     *
     * @param header заголовок, называющий владельца запроса
     * @param value  значение области
     */
    public void scope(String header, String value) {
        this.scopeHeader = header;
        this.scopeValue = value;
    }

    /**
     * Объявляет путь общим: ответ на нём области не знает — запрос без
     * подписи счёта ни одному счёту не адресован.
     *
     * @param path путь
     */
    public void shared(String path) {
        sharedPaths.add(path);
    }

    /** Отвечает на {@code GET} по пути телом JSON. */
    public void answers(String path, String json) {
        server.stubFor(scoped(WireMock.get(WireMock.urlPathEqualTo(path)), path).willReturn(WireMock.okJson(json)));
    }

    /** Отвечает на {@code POST} по пути телом JSON. */
    public void answersPost(String path, String json) {
        server.stubFor(scoped(WireMock.post(WireMock.urlPathEqualTo(path)), path).willReturn(WireMock.okJson(json)));
    }

    /**
     * Отвечает на {@code GET} по пути телом JSON, собранным шаблоном: момент
     * ответа площадки ставится моментом запроса, и постоянное тело состарилось
     * бы за время тропы.
     *
     * @param path     путь
     * @param template тело с выражениями шаблонизатора WireMock
     */
    public void answersTemplated(String path, String template) {
        server.stubFor(scoped(WireMock.get(WireMock.urlPathEqualTo(path)), path)
                .willReturn(WireMock.okJson(template).withTransformers(TEMPLATE)));
    }

    /**
     * Отвечает на {@code POST} по пути телом JSON, собранным шаблоном по
     * запросу: площадка эхом возвращает клиентский идентификатор команды, и
     * постоянное тело эха не дало бы.
     *
     * @param path     путь
     * @param template тело с выражениями шаблонизатора WireMock
     */
    public void answersPostTemplated(String path, String template) {
        server.stubFor(scoped(WireMock.post(WireMock.urlPathEqualTo(path)), path)
                .willReturn(WireMock.okJson(template).withTransformers(TEMPLATE)));
    }

    /**
     * Отвечает на {@code GET} по пути, когда параметр запроса равен
     * значению, — раньше ответа пути без условия: площадка отдаёт каждую
     * сущность по её идентификатору.
     *
     * @param path  путь
     * @param param имя параметра запроса
     * @param value значение параметра
     * @param json  тело
     */
    public void answersWhere(String path, String param, String value, String json) {
        server.stubFor(scoped(WireMock.get(WireMock.urlPathEqualTo(path)), path)
                .withQueryParam(param, WireMock.equalTo(value))
                .atPriority(WHERE)
                .willReturn(WireMock.okJson(json).withTransformers(TEMPLATE)));
    }

    /**
     * Отвечает на {@code GET} по пути, когда параметра в запросе НЕТ, — раньше
     * ответа пути без условия, но позже ответа по значению параметра:
     * площадка ищет сущность тем идентификатором, который ей дали, и на поиск
     * одним нашим идентификатором не отдаёт сущность, которой под ним не знает.
     *
     * @param path  путь
     * @param param имя параметра, которого в запросе нет
     * @param json  тело
     */
    public void answersWithout(String path, String param, String json) {
        server.stubFor(scoped(WireMock.get(WireMock.urlPathEqualTo(path)), path)
                .withQueryParam(param, WireMock.absent())
                .atPriority(WITHOUT)
                .willReturn(WireMock.okJson(json).withTransformers(TEMPLATE)));
    }

    /**
     * Отвечает на {@code GET} по пути, пока сценарий площадки стои́т в
     * названном состоянии, — раньше ответа по параметру и ответа пути:
     * состояние сущности у площадки меняет принятая команда, а не тест.
     *
     * @param path     путь
     * @param scenario имя сценария
     * @param state    состояние сценария
     * @param json     тело
     */
    public void answersInState(String path, String scenario, String state, String json) {
        server.stubFor(scoped(WireMock.get(WireMock.urlPathEqualTo(path)), path)
                .inScenario(scenario(scenario))
                .whenScenarioStateIs(state)
                .atPriority(IN_STATE)
                .willReturn(WireMock.okJson(json).withTransformers(TEMPLATE)));
    }

    /**
     * Отвечает на {@code GET} по пути, когда параметр запроса равен значению и
     * сценарий площадки стои́т в названном состоянии, — раньше всех прочих
     * ответов пути: принятая команда меняет чтение ОДНОЙ сущности, а чтения
     * соседних сущностей того же пути остаются прежними.
     *
     * @param path     путь
     * @param param    имя параметра запроса
     * @param value    значение параметра
     * @param scenario имя сценария
     * @param state    состояние сценария
     * @param json     тело
     */
    public void answersWhereInState(String path, String param, String value, String scenario, String state,
                                    String json) {
        server.stubFor(scoped(WireMock.get(WireMock.urlPathEqualTo(path)), path)
                .withQueryParam(param, WireMock.equalTo(value))
                .inScenario(scenario(scenario))
                .whenScenarioStateIs(state)
                .atPriority(IN_STATE)
                .willReturn(WireMock.okJson(json).withTransformers(TEMPLATE)));
    }

    /**
     * Отвечает на {@code POST} по пути, чьё тело несёт значение по пути JSON,
     * и переводит сценарий площадки из одного состояния в другое: принятая
     * команда меняет то, что площадка отдаёт на чтения. В прочих состояниях
     * команду принимает ответ пути без условия.
     *
     * @param path     путь
     * @param jsonPath путь JSON в теле запроса
     * @param value    значение по нему
     * @param scenario имя сценария
     * @param from     состояние, из которого сценарий переходит
     * @param to       состояние, в которое сценарий переходит
     * @param json     тело ответа — шаблоном по запросу
     */
    public void answersPostMoving(String path, String jsonPath, String value, String scenario, String from,
                                  String to, String json) {
        server.stubFor(scoped(WireMock.post(WireMock.urlPathEqualTo(path)), path)
                .withRequestBody(WireMock.matchingJsonPath(jsonPath, WireMock.equalTo(value)))
                .inScenario(scenario(scenario))
                .whenScenarioStateIs(from)
                .willSetStateTo(to)
                .atPriority(WHERE)
                .willReturn(WireMock.okJson(json).withTransformers(TEMPLATE)));
    }

    /**
     * На первый {@code POST} по пути рвёт соединение, дальше отвечает прежним
     * ответом пути: команда, не дошедшая до ответа, после которой площадка
     * жива.
     *
     * @param path путь
     */
    public void failsPostTransportOnce(String path) {
        server.stubFor(scoped(WireMock.post(WireMock.urlPathEqualTo(path)), path)
                .inScenario(scenario("post " + path))
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo(ANSWERING));
    }

    /**
     * На первое обращение по пути — любым глаголом — рвёт соединение, на
     * следующие отвечает телом: отказ транспорта, после которого сосед жив.
     *
     * @param path путь
     * @param json тело штатного ответа
     */
    public void failsTransportThenAnswers(String path, String json) {
        server.stubFor(scoped(WireMock.any(WireMock.urlPathEqualTo(path)), path)
                .inScenario(scenario(path))
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo(ANSWERING));
        server.stubFor(scoped(WireMock.any(WireMock.urlPathEqualTo(path)), path)
                .inScenario(scenario(path))
                .whenScenarioStateIs(ANSWERING)
                .willReturn(WireMock.okJson(json)));
        server.resetScenario(scenario(path));
    }

    /**
     * Обращения со времени последнего забывания — в порядке прихода; под
     * областью — её обращения и обращения без заголовка области.
     */
    public List<LoggedRequest> requests() {
        return server.getAllServeEvents().reversed().stream()
                .map(ServeEvent::getRequest)
                .filter(this::inScope)
                .toList();
    }

    /** Обращения по пути — в порядке прихода. */
    public List<LoggedRequest> requests(String path) {
        return requests().stream()
                .filter(request -> Objects.equals(path, request.getUrl().split("\\?")[0]))
                .toList();
    }

    /** Возвращает сценарии в начальное состояние, не трогая ответов; под областью — только её. */
    public void forgetScenarios() {
        if (isNull(scopeValue)) {
            server.resetScenarios();
            return;
        }
        server.getAllScenarios().getScenarios().stream()
                .map(Scenario::getName)
                .filter(scenario -> scenario.startsWith(scenario("")))
                .forEach(server::resetScenario);
    }

    /** Забывает журнал обращений, не трогая ответов. */
    public void forgetRequests() {
        server.resetRequests();
    }

    /** Останавливает стаб. */
    public void stop() {
        server.stop();
    }

    private MappingBuilder scoped(MappingBuilder mapping, String path) {
        if (isNull(scopeValue) || isTrue(sharedPaths.contains(path))) {
            return mapping;
        }
        return mapping.withHeader(scopeHeader, WireMock.equalTo(scopeValue));
    }

    private String scenario(String scenario) {
        return isNull(scopeValue) ? scenario : scopeValue + " " + scenario;
    }

    private Boolean inScope(LoggedRequest request) {
        if (isNull(scopeValue)) {
            return Boolean.TRUE;
        }
        String owner = request.getHeader(scopeHeader);
        return isNull(owner) || Objects.equals(scopeValue, owner);
    }
}
