package com.example.tests.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Objects;

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
 */
public final class Stub {

    private static final String ANSWERING = "answering";

    private final String name;
    private final WireMockServer server;
    private final String host;

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

    /** Отвечает на {@code GET} по пути телом JSON. */
    public void answers(String path, String json) {
        server.stubFor(WireMock.get(WireMock.urlPathEqualTo(path)).willReturn(WireMock.okJson(json)));
    }

    /** Отвечает на {@code POST} по пути телом JSON. */
    public void answersPost(String path, String json) {
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(path)).willReturn(WireMock.okJson(json)));
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
        server.stubFor(WireMock.get(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.okJson(template).withTransformers("response-template")));
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
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.okJson(template).withTransformers("response-template")));
    }

    /**
     * На первый {@code POST} по пути рвёт соединение, дальше отвечает прежним
     * ответом пути: команда, не дошедшая до ответа, после которой площадка
     * жива.
     *
     * @param path путь
     */
    public void failsPostTransportOnce(String path) {
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(path))
                .inScenario("post " + path)
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
        server.resetScenarios();
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .inScenario(path)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo(ANSWERING));
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .inScenario(path)
                .whenScenarioStateIs(ANSWERING)
                .willReturn(WireMock.okJson(json)));
    }

    /** Все обращения со времени последнего забывания — в порядке прихода. */
    public List<LoggedRequest> requests() {
        return server.getAllServeEvents().reversed().stream()
                .map(ServeEvent::getRequest)
                .toList();
    }

    /** Обращения по пути — в порядке прихода. */
    public List<LoggedRequest> requests(String path) {
        return requests().stream()
                .filter(request -> Objects.equals(path, request.getUrl().split("\\?")[0]))
                .toList();
    }

    /** Забывает журнал обращений, не трогая ответов. */
    public void forgetRequests() {
        server.resetRequests();
    }

    /** Останавливает стаб. */
    public void stop() {
        server.stop();
    }
}
