package com.example.strategies.box;

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
 * <p><b>Сосед у владельца определений ОДИН — `trading-core`</b>, и это
 * само по себе предмет: второй сосед (`market-data`, история для
 * бэктеста) приезжает фазой 4, и у ящика он — отрицательное ожидание
 * (.claude/tests/cases/strategies.md §«Чем достаются выходы»). Адрес
 * подаётся тем же свойством {@code neighbours.trading-core.base-url},
 * которым его подаёт манифест окружения.
 *
 * <p><b>Исходящий вызов есть ВЫХОД создания и активации.</b> Операнды
 * обеих проверок живут у ядра, и часть ожиданий — «к соседу не ушло ни
 * одного запроса», «ушли ровно два» — наблюдается только записями стаба.
 *
 * <p><b>Стаб общий на JVM и сбрасывается перед каждой клеткой.</b> Иначе
 * отрицание «иных запросов к соседу нет» мерило бы весь прогон, а не вход
 * клетки.
 *
 * <p><b>Порядок запросов берётся из самой выдачи, а не сортировкой по
 * времени записи.</b> Метка события у стаба миллисекундна, а два вызова
 * создания уходят внутри одного миллисекунда: сортировка по ней не
 * устойчива, и клетка, утверждающая о ПОСЛЕДОВАТЕЛЬНОСТИ, краснела бы
 * через раз. Выдача стаба идёт от свежего к старому — разворот даёт
 * хронологию.
 *
 * <p><b>Открытого HTTP/2 у стаба нет, и это верность предмету, а не
 * настройка.</b> Сосед по ярусу — сервис на том же каркасе, и открытый
 * (без TLS) HTTP/2 у него выключен умолчанием; стаб, поднявший его,
 * предъявлял бы клиенту транспорт, которого в контуре нет.
 *
 * <p><b>Ответы собираются от КОНТРАКТА соседа</b>, а не сняты с живого
 * сервиса: предметом здесь является владелец определений, и форма ответа
 * соседа ему вход, а не выход. Что сосед эту форму и отдаёт, мерит ящик
 * соседа.
 */
final class PeerStub {

    private static final PeerStub TRADING_CORE = new PeerStub("trading-core");

    /**
     * Второй сосед яруса — и он поднят РАДИ ОТРИЦАНИЯ.
     *
     * <p>Адреса его сервису не подаёт ни одна ось конфигурации, и это не
     * недосмотр, а предмет клетки {@code B10.2}: у владельца определений
     * сосед ровно один, а история для бэктеста приезжает со своим
     * предметом фазой 4. Поднятый и пустой стаб делает утверждение
     * наблюдаемым; не подняв его, кейс утверждал бы об отсутствии того,
     * чего не с чем было бы сверить.
     */
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

    /** Стаб торгового ядра: разрешимость ссылок и числа риск-аппетита. */
    static PeerStub tradingCore() {
        return TRADING_CORE;
    }

    /** Стаб владельца рыночных данных: второй сосед яруса — см. поле. */
    static PeerStub marketData() {
        return MARKET_DATA;
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
     * определения ставятся тропой самого ящика — созданием через
     * поверхность, — и их вызовы попадают в тот же журнал стаба.
     * Отрицание «запросов нет» иначе не сошлось бы никогда.
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

    /** Сколько запросов стаб получил всего. */
    Integer count() {
        return requests().size();
    }
}
