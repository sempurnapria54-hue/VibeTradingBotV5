package com.example.bff.box;

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
 * Стаб поверхностей ВЛАДЕЛЬЦЕВ за периметром — все шесть одним
 * механизмом (.claude/decisions/test-contour-design-pass.md, решение 3;
 * .claude/tests/cases/bff.md §«Чем достаются выходы»).
 *
 * <p><b>Стаб один, потому что и ось конфигурации одна.</b> Адрес
 * владельца приезжает периметру не шестью свойствами, а ШАБЛОНОМ с
 * плейсхолдером имени ({@code perimeter.owner-url-template}): перечня
 * владельцев периметр не держит, и новый владелец правки периметра не
 * требует (docs/architecture/contracts.md §«Владельца называет путь, а не
 * таблица маршрутов»). Шесть отдельных стабов потребовали бы шести
 * свойств — то есть подменили бы предмет.
 *
 * <p><b>Имя владельца стои́т ПРИСТАВКОЙ ПУТИ, и это не обход, а способ
 * наблюдать адресацию.</b> Шаблон прогона —
 * {@code http://localhost:<порт>/owner/{owner}}, поэтому у каждого
 * полученного запроса имя адресата читается первым сегментом, а не
 * выводится из совпадения портов. Клетка «ушло владельцу X» тем самым
 * становится утверждением о РАЗРЕШЁННОМ адресе, а не о том, что запрос
 * вообще куда-то ушёл.
 *
 * <p><b>Стаб общий на JVM и сбрасывается перед каждой клеткой.</b> Иначе
 * отрицание «к прочим владельцам запросов нет» мерило бы весь прогон, а
 * не вход клетки.
 *
 * <p><b>Порядок запросов берётся из самой выдачи, а не сортировкой по
 * времени записи.</b> Метка события у стаба миллисекундна, а два вызова
 * уходят внутри одного миллисекунда: сортировка по ней не устойчива, и
 * клетка, утверждающая о ПОСЛЕДОВАТЕЛЬНОСТИ, краснела бы через раз.
 * Выдача стаба идёт от свежего к старому — разворот даёт хронологию.
 *
 * <p><b>Открытого HTTP/2 у стаба нет, и это верность предмету, а не
 * настройка.</b> Владелец за периметром — сервис на том же каркасе, и
 * открытый (без TLS) HTTP/2 у него выключен умолчанием; стаб, поднявший
 * его, предъявлял бы клиенту транспорт, которого в контуре нет.
 */
final class OwnerStub {

    /** Владелец «кто есть кто»: у него периметр читает членства. */
    static final String AUTH = "auth";

    /** Владелец определений стратегий. */
    static final String STRATEGIES = "strategies";

    /** Владелец сделок и торгового состояния. */
    static final String TRADING_CORE = "trading-core";

    /** Владелец рыночных данных. */
    static final String MARKET_DATA = "market-data";

    /** Владелец журнала аудита. */
    static final String AUDIT = "audit";

    /** Владелец агрегатов статистики. */
    static final String STATISTICS = "statistics";

    /** Точка резолва членств предъявителя у владельца «кто есть кто». */
    static final String MEMBERSHIPS_PATH = "/api/v1/auth/memberships/self";

    /** Приставка пути, по которой читается имя адресата. */
    private static final String OWNER_PREFIX = "/owner/";

    private static final OwnerStub INSTANCE = new OwnerStub();

    private final WireMockServer server;

    private OwnerStub() {
        this.server = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .http2PlainDisabled(true));
        this.server.start();
    }

    static OwnerStub stub() {
        return INSTANCE;
    }

    /** Шаблон адреса владельца: он же значение {@code perimeter.owner-url-template}. */
    String urlTemplate() {
        return "http://localhost:" + server.port() + OWNER_PREFIX + "{owner}";
    }

    /** Забывает и заготовки ответов, и записи запросов. */
    void reset() {
        server.resetAll();
    }

    /**
     * Забывает только записи запросов; заготовки ответов остаются.
     *
     * <p>Этим ходом кейс отделяет свои запросы от запросов ПРЕДУСЛОВИЯ:
     * резолв контекста уходит в тот же журнал стаба, что и пересылка, и
     * отрицание «запросов нет» иначе не сошлось бы никогда.
     */
    void forgetRequests() {
        server.resetRequests();
    }

    /** Ответ {@code 200} с телом JSON на любой глагол по этому пути владельца. */
    void answers(String owner, String path, String body) {
        answers(owner, path, 200, body);
    }

    /** Ответ названным статусом и телом JSON на любой глагол по этому пути владельца. */
    void answers(String owner, String path, Integer status, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(OWNER_PREFIX + owner + path))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** Ответ названным статусом, типом содержимого и телом дословно. */
    void answersWith(String owner, String path, Integer status, String contentType, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathEqualTo(OWNER_PREFIX + owner + path))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", contentType)
                        .withBody(body)));
    }

    /** Ответ на ЛЮБОЙ путь этого владельца: вход клеток о пересылке как есть. */
    void answersAnything(String owner, Integer status, String body) {
        server.stubFor(WireMock.any(WireMock.urlPathMatching(OWNER_PREFIX + owner + "/.*"))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /**
     * Отказ ТРАНСПОРТА на любом пути владельца: соединение рвётся, ответа
     * нет вовсе.
     *
     * <p>Это не ответ со статусом: недоступность соседа и его решение —
     * разные исходы, и ответ {@code 503} проверял бы второй вместо
     * первого.
     */
    void failsTransport(String owner) {
        server.stubFor(WireMock.any(WireMock.urlPathMatching(OWNER_PREFIX + owner + "/.*"))
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
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

    /** Запросы, адресованные названному владельцу, в порядке получения. */
    List<LoggedRequest> requests(String owner) {
        return requests().stream()
                .filter(request -> Objects.equals(owner, ownerOf(request)))
                .toList();
    }

    /** Запросы владельца по названному его пути, в порядке получения. */
    List<LoggedRequest> requests(String owner, String path) {
        return requests(owner).stream()
                .filter(request -> Objects.equals(path, pathOf(request)))
                .toList();
    }

    /** Единственный запрос владельца по пути; иное число — падение. */
    LoggedRequest single(String owner, String path) {
        List<LoggedRequest> found = requests(owner, path);
        if (found.size() != 1) {
            throw new AssertionError("Ожидался ровно один запрос к владельцу " + owner + " на " + path
                    + ", пришло " + found.size() + ": " + addresses());
        }
        return found.getFirst();
    }

    /** Адреса всех полученных запросов — «владелец путь», в порядке получения. */
    List<String> addresses() {
        return requests().stream()
                .map(request -> ownerOf(request) + " " + pathOf(request))
                .toList();
    }

    /** Имена владельцев, которым уходили запросы, в порядке получения. */
    List<String> addressedOwners() {
        return requests().stream().map(OwnerStub::ownerOf).toList();
    }

    /** Сколько запросов стаб получил всего. */
    Integer count() {
        return requests().size();
    }

    /** Имя владельца, которому запрос был адресован: первый сегмент после приставки. */
    static String ownerOf(LoggedRequest request) {
        String url = withoutPrefix(request);
        int slashAt = url.indexOf('/');
        return slashAt < 0 ? url : url.substring(0, slashAt);
    }

    /** Путь запроса у владельца — без приставки и без строки запроса. */
    static String pathOf(LoggedRequest request) {
        return fullPathOf(request).split("\\?")[0];
    }

    /** Путь запроса у владельца ВМЕСТЕ со строкой запроса: её пересылка — своя клетка. */
    static String fullPathOf(LoggedRequest request) {
        String url = withoutPrefix(request);
        int slashAt = url.indexOf('/');
        return slashAt < 0 ? "" : url.substring(slashAt);
    }

    private static String withoutPrefix(LoggedRequest request) {
        String url = request.getUrl();
        return url.startsWith(OWNER_PREFIX) ? url.substring(OWNER_PREFIX.length()) : url;
    }
}
