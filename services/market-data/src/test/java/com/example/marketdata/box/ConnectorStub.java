package com.example.marketdata.box;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Стаб поверхности коннектора — единственного соседа этого сервиса
 * (.claude/decisions/test-contour-design-pass.md, решение 3) — и второй
 * наблюдатель предмета после базы.
 *
 * <p><b>Исходящее чтение есть ВЫХОД тика.</b> Почти всё, что сервис
 * производит, производит тик по расписанию, а не вызов поверхности; и
 * большая часть отрицаний группы тиков — «запроса правил после отказа
 * нет», «по третьему инструменту запроса нет», «чтений площадки на этом
 * тике нет» — наблюдается только записями стаба.
 *
 * <p><b>Стаб общий на JVM и сбрасывается перед каждой клеткой.</b> Иначе
 * отрицание «иных запросов к коннектору нет» мерило бы весь прогон, а не
 * вход клетки.
 *
 * <p><b>Порядок запросов берётся из самой выдачи, а не сортировкой по
 * времени записи.</b> Метка времени события у стаба миллисекундна, а
 * вызовы прохода уходят пачкой внутри одного миллисекунда: сортировка по
 * ней не устойчива, и клетка, утверждающая о ПОСЛЕДОВАТЕЛЬНОСТИ
 * (окно за курсором, обход книг, порядок листинга), краснела бы через
 * раз. Выдача стаба идёт от свежего к старому — разворот даёт хронологию.
 *
 * <p><b>Ответы собираются от КОНТРАКТА соседа</b>, а не сняты с живого
 * коннектора: предметом здесь является market-data, и форма ответа соседа
 * ему вход, а не выход. Что сосед эту форму и отдаёт, мерит ящик соседа.
 */
final class ConnectorStub {

    /** Листинг инструментов площадки по типу инструмента. */
    static final String INSTRUMENTS = "/api/v1/market/instruments";

    /** Справочные правила инструмента: путь строится по имени инструмента. */
    static final String RULES_SUFFIX = "/rules";

    /** Последние закрытые свечи. */
    static final String CANDLES = "/api/v1/market/candles";

    /** Исторические закрытые свечи окном назад. */
    static final String HISTORY_CANDLES = "/api/v1/market/candles/history";

    /** Тикеры всего листинга одним чтением. */
    static final String TICKERS = "/api/v1/market/tickers";

    /** Марк-цены листинга. */
    static final String MARK_PRICES = "/api/v1/market/mark-prices";

    /** Цены индексов одной котировочной валюты. */
    static final String INDEX_PRICES = "/api/v1/market/index-prices";

    /** Книга заявок инструмента: путь строится по имени инструмента. */
    static final String ORDER_BOOK = "/api/v1/market/order-book";

    /** Цены момента инструмента: путь строится по имени инструмента. */
    static final String PRICES = "/api/v1/market/prices";

    private static final ConnectorStub INSTANCE = new ConnectorStub();

    private final WireMockServer server;

    private ConnectorStub() {
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        this.server.start();
    }

    static ConnectorStub stub() {
        return INSTANCE;
    }

    /** Базовый адрес коннектора: он же значение connector.base-url сервиса. */
    String baseUrl() {
        return "http://localhost:" + server.port();
    }

    /** Путь справочных правил названного инструмента. */
    static String rulesOf(String externalInstrumentId) {
        return INSTRUMENTS + "/" + externalInstrumentId + RULES_SUFFIX;
    }

    /** Путь книги заявок названного инструмента. */
    static String orderBookOf(String externalInstrumentId) {
        return ORDER_BOOK + "/" + externalInstrumentId;
    }

    /** Путь цен момента названного инструмента. */
    static String pricesOf(String externalInstrumentId) {
        return PRICES + "/" + externalInstrumentId;
    }

    /** Забывает и заготовки ответов, и записи запросов. */
    void reset() {
        server.resetAll();
    }

    /**
     * Забывает только записи запросов; заготовки ответов остаются.
     *
     * <p>Этим ходом кейс отделяет свои запросы от запросов ПРЕДУСЛОВИЯ:
     * каталог и ряды ставятся тропами самого ящика, то есть тиками, и их
     * чтения попадают в тот же журнал стаба. Отрицание «запросов нет»
     * иначе не сошлось бы никогда.
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

    /**
     * Ответ, зависящий от значения query-параметра: им подаются кейсы, где
     * один путь опрашивается несколькими ногами подряд (типы инструментов,
     * котировочные валюты, окна истории).
     */
    void answersWhen(String path, String parameter, String value, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .withQueryParam(parameter, WireMock.equalTo(value))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** Отказ, зависящий от значения query-параметра: та же пара, что выше. */
    void answersWhen(String path, String parameter, String value, Integer status, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .withQueryParam(parameter, WireMock.equalTo(value))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
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

    /** Единственный запрос по пути; иное число — падение с именем пути. */
    LoggedRequest single(String path) {
        List<LoggedRequest> found = requests(path);
        if (found.size() != 1) {
            throw new AssertionError("Ожидался ровно один запрос на " + path + ", пришло " + found.size()
                    + ": " + requests().stream().map(LoggedRequest::getUrl).toList());
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

    /** Пути всех полученных запросов, в порядке получения. */
    List<String> paths() {
        return requests().stream().map(request -> request.getUrl().split("\\?")[0]).toList();
    }

    /** Адреса всех полученных запросов вместе с query, в порядке получения. */
    List<String> urls() {
        return requests().stream().map(LoggedRequest::getUrl).toList();
    }
}
