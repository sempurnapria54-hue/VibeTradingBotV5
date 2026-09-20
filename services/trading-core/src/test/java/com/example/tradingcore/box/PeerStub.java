package com.example.tradingcore.box;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Стаб поверхности соседа по ярусу — и второй наблюдатель предмета после
 * базы (.claude/decisions/test-contour-design-pass.md, решение 3).
 *
 * <p><b>Соседей у ядра три, и подменяются все без исключения:</b>
 * коннектор (команды площадке и добыча факта), {@code auth} (реестр
 * биржевых счетов), {@code market-data} (фичи, каталог, биржевые правила,
 * требования рядов). Адреса подаются теми же свойствами
 * {@code neighbours.*}, которыми их подаёт манифест окружения.
 *
 * <p><b>Исходящий вызов есть ВЫХОД тика.</b> Почти всё, что ядро
 * производит, производит проход по расписанию, а не вызов поверхности; и
 * большая часть отрицаний — «на биржу не ходит», «вторую команду не
 * шлёт», «чтений каталога на этом тике нет» — наблюдается только записями
 * стаба.
 *
 * <p><b>Стабы общие на JVM и сбрасываются перед каждой клеткой.</b> Иначе
 * отрицание «иных запросов к соседу нет» мерило бы весь прогон, а не вход
 * клетки.
 *
 * <p><b>Порядок запросов берётся из самой выдачи, а не сортировкой по
 * времени записи.</b> Метка события у стаба миллисекундна, а вызовы
 * прохода уходят пачкой внутри одного миллисекунда: сортировка по ней не
 * устойчива, и клетка, утверждающая о ПОСЛЕДОВАТЕЛЬНОСТИ (порядок ног
 * замещения, порядок выхода, порядок шагов прохода), краснела бы через
 * раз. Выдача стаба идёт от свежего к старому — разворот даёт хронологию.
 *
 * <p><b>Открытого HTTP/2 у стаба нет, и это верность предмету, а не
 * настройка.</b> Сосед по ярусу — сервис на том же каркасе, и открытый
 * (без TLS) HTTP/2 у него выключен умолчанием; стаб, поднявший его,
 * предъявлял бы клиенту ядра транспорт, которого в контуре нет — и
 * ронял бы всякий вызов С ТЕЛОМ обрывом потока на апгрейде, то есть
 * выдавал бы недоступность соседа там, где сосед исправен.
 *
 * <p><b>Ответы собираются от КОНТРАКТА соседа</b>, а не сняты с живого
 * сервиса: предметом здесь является ядро, и форма ответа соседа ему вход,
 * а не выход. Что сосед эту форму и отдаёт, мерит ящик соседа.
 */
final class PeerStub {

    private static final PeerStub CONNECTOR = new PeerStub("connector");

    private static final PeerStub AUTH = new PeerStub("auth");

    private static final PeerStub MARKET_DATA = new PeerStub("market-data");

    private final String name;
    private final WireMockServer server;

    private PeerStub(String name) {
        this.name = name;
        this.server = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .http2PlainDisabled(true));
        this.server.start();
    }

    /** Стаб коннектора: команды площадке и добыча фактов. */
    static PeerStub connector() {
        return CONNECTOR;
    }

    /** Стаб владельца реестра биржевых счетов. */
    static PeerStub auth() {
        return AUTH;
    }

    /** Стаб владельца рыночных данных. */
    static PeerStub marketData() {
        return MARKET_DATA;
    }

    /** Все три стаба соседей. */
    static List<PeerStub> all() {
        return List.of(CONNECTOR, AUTH, MARKET_DATA);
    }

    /** Имя соседа: им называется несошедшееся ожидание. */
    String name() {
        return name;
    }

    /** Базовый адрес соседа: он же значение его {@code neighbours.*.base-url}. */
    String baseUrl() {
        return "http://localhost:" + server.port();
    }

    /** Забывает и заготовки ответов, и записи запросов. */
    void reset() {
        server.resetAll();
    }

    /**
     * Забывает только записи запросов; заготовки ответов остаются.
     *
     * <p>Этим ходом кейс отделяет свои запросы от запросов ПРЕДУСЛОВИЯ:
     * проекции, сделки и транши ставятся тропами самого ящика, то есть
     * тиками, и их вызовы попадают в тот же журнал стаба. Отрицание
     * «запросов нет» иначе не сошлось бы никогда.
     */
    void forgetRequests() {
        server.resetRequests();
    }

    /** Ответ {@code 200} с заданным телом на любой метод по этому пути. */
    void answers(String path, String body) {
        answers(path, 200, body);
    }

    /** Ответ заданным статусом и телом на любой метод по этому пути. */
    void answers(String path, Integer status, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** Ответ {@code 200} на любой путь с названным префиксом. */
    void answersUnder(String pathPrefix, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathMatching(pathPrefix + ".*"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** Ответ на что угодно: им ставится «сосед отвечает и молчит о деталях». */
    void answersAnything(String body) {
        server.stubFor(WireMock.any(WireMock.anyUrl())
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** Отказ на что угодно: им ставится недоступность соседа целиком. */
    void refusesAnything(Integer status, String body) {
        server.stubFor(WireMock.any(WireMock.anyUrl())
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** Ответ, зависящий от значения query-параметра. */
    void answersWhen(String path, String parameter, String value, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .withQueryParam(parameter, WireMock.equalTo(value))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /**
     * Ответы по очереди: первый вызов получает первое тело, второй —
     * второе и так далее; последнее держится дальше.
     *
     * <p>Им подаются кейсы, где один путь опрашивается несколькими
     * проходами подряд и ответ обязан измениться между ними (добыча факта
     * по бюджету, повторная отправка, второй проход после починки).
     */
    void answersInTurn(String path, String... bodies) {
        String scenario = name + ":" + path;
        for (int index = 0; index < bodies.length; index++) {
            String from = index == 0 ? com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
                    : "turn-" + index;
            String to = index == bodies.length - 1 ? from : "turn-" + (index + 1);
            server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                    .inScenario(scenario)
                    .whenScenarioStateIs(from)
                    .willSetStateTo(to)
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(bodies[index])));
        }
    }

    /**
     * Ответ с задержкой: вход клеток о перекрывающем запуске. Задержка
     * стои́т на СТАБЕ, а не в тесте, — иначе перекрытие пришлось бы ловить
     * гонкой, а её исход не детерминирован.
     */
    void answersSlowly(String path, String body, Integer delayMillis) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(delayMillis)
                        .withBody(body)));
    }

    /**
     * Все запросы, полученные стабом с последнего сброса, в порядке
     * получения (разворот выдачи — см. шапку класса).
     */
    List<LoggedRequest> requests() {
        List<ServeEvent> events = server.getAllServeEvents();
        return IntStream.range(0, events.size())
                .mapToObj(index -> events.get(events.size() - 1 - index).getRequest())
                .toList();
    }

    /** Запросы по одному пути, в порядке получения. */
    List<LoggedRequest> requests(String path) {
        return requests().stream()
                .filter(request -> Objects.equals(path, request.getUrl().split("\\?")[0]))
                .toList();
    }

    /** Запросы, чей путь начинается названным префиксом, в порядке получения. */
    List<LoggedRequest> requestsUnder(String pathPrefix) {
        return requests().stream()
                .filter(request -> request.getUrl().split("\\?")[0].startsWith(pathPrefix))
                .toList();
    }

    /** Пути всех полученных запросов в порядке получения. */
    List<String> paths() {
        return requests().stream().map(request -> request.getUrl().split("\\?")[0]).toList();
    }

    /** Единственный запрос по пути; иное число — падение с именем пути. */
    LoggedRequest single(String path) {
        List<LoggedRequest> found = requests(path);
        if (found.size() != 1) {
            throw new AssertionError("Ожидался ровно один запрос на " + path + " у соседа " + name
                    + ", пришло " + found.size() + ": " + paths());
        }
        return found.getFirst();
    }

    /** Сколько запросов стаб получил по пути. */
    Integer count(String path) {
        return requests(path).size();
    }

    /** Сколько запросов стаб получил всего. */
    Integer count() {
        return requests().size();
    }
}
