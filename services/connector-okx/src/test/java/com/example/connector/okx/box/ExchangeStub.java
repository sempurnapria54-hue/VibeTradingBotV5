package com.example.connector.okx.box;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Стаб площадки OKX (.claude/decisions/test-contour-design-pass.md,
 * решение 3) — и одновременно ГЛАВНЫЙ НАБЛЮДАТЕЛЬ этого предмета.
 *
 * <p><b>Исходящий вызов к площадке есть ВЫХОД ящика.</b> У сервиса нет ни
 * базы, ни событий: всё, что он производит сверх ответа своей поверхности,
 * — запрос наружу. Поэтому записи стаба здесь не вспомогательная оснастка,
 * а тот самый наблюдаемый след, которым мерится большинство клеток: путь,
 * метод, query, тело, заголовки подписи и заголовок контура.
 *
 * <p><b>Стаб общий на JVM и сбрасывается перед каждой клеткой.</b> Иначе
 * отрицание «иных запросов к площадке нет» мерило бы весь прогон, а не
 * вход клетки: у стаба, в отличие от хранилища, разностной формы не нужно
 * — состояние снимается целиком и дёшево.
 *
 * <p><b>Ответы собираются от КОНТРАКТА, а не сняты с площадки</b>, и это
 * названная цена: прогонов мишени {@code -D} ещё не было, снимать нечего
 * (§«Две мишени и суффикс метки» документа кейсов). Запись, снятая первым
 * прогоном {@code -D}, эти сборки заменяет.
 */
final class ExchangeStub {

    private static final ExchangeStub INSTANCE = new ExchangeStub();

    private final WireMockServer server;

    private ExchangeStub() {
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        this.server.start();
    }

    static ExchangeStub stub() {
        return INSTANCE;
    }

    /** Базовый адрес площадки: он же значение {@code okx.base-url} сервиса. */
    String baseUrl() {
        return "http://localhost:" + server.port();
    }

    /** Забывает и заготовки ответов, и записи запросов. */
    void reset() {
        server.resetAll();
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
     * один путь опрашивается несколькими ногами подряд (семьи algo,
     * состояния истории, страницы пагинации).
     */
    void answersWhen(String path, String parameter, String value, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .withQueryParam(parameter, WireMock.equalTo(value))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /**
     * Ответ на запрос БЕЗ заданного query-параметра: пара к
     * {@link #answersWhen}, которой подаётся первая страница обхода.
     */
    void answersWithout(String path, String parameter, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .withQueryParam(parameter, WireMock.absent())
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /**
     * Соединение обрывается без ответа: вход клетки «площадка не
     * ответила». Обрыв, а не таймаут — таймаут стои́т кейсу своего
     * ожидания, а предмет у них один.
     */
    void breaks(String path) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /**
     * Все запросы, полученные стабом с последнего сброса, в порядке
     * получения.
     *
     * <p><b>Порядок берётся из самой выдачи, а не сортировкой по времени
     * записи.</b> Метка времени события у стаба миллисекундная, а вызовы
     * обхода уходят пачкой внутри одного миллисекунда: сортировка по ней
     * не устойчива, и клетка, утверждающая о ПОСЛЕДОВАТЕЛЬНОСТИ запросов
     * (курсор пагинации, ноги истории, семьи среза), краснела бы через
     * раз. Выдача стаба идёт от свежего к старому — разворот даёт
     * хронологию.
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

    /** Сколько запросов стаб получил всего. */
    Integer count() {
        return requests().size();
    }
}
