package com.example.connector.okx.box;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `connector-okx`: реальный контекст сервиса со ВСЕМИ его
 * бинами, наблюдаемый только снаружи — своей поверхностью, стабом площадки
 * и хранилищем (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний
 * бин не подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по
 * адресу случайного порта, а не {@code MockMvc} и не автовайренный
 * {@code ExchangeGateway}. Наблюдатели субстрата ({@link ExchangeStub},
 * {@link SecretStore}) ходят своим соединением — ящик смотрит на субстрат
 * снаружи.
 *
 * <p><b>Свойств контекста здесь не объявлено ни одного, и это несущее.</b>
 * Перечень свойств задаёт КАЖДЫЙ класс кейсов своим методом
 * {@code @DynamicPropertySource}: положение осей окружения есть ВХОД
 * ящика, а унаследованный метод реестра свойств перекрывал бы свои
 * переопределения в порядке, которого контракт реестра не обещает.
 * Цена названа — контекст на класс конфигурации, а не один на прогон;
 * классы, которым довольно штатного положения осей, берут его
 * {@link SharedConnectorBox} и делят один контекст.
 *
 * <p><b>Стаб площадки сбрасывается перед каждой клеткой.</b> У этого
 * предмета исходящий запрос — главный наблюдаемый выход, и отрицание
 * «иных запросов нет» без сброса мерило бы весь прогон.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ConnectorBox {

    /** Счёт штатного положения осей: контур боевой. */
    protected static final String ACCOUNT = "ACC-1";

    /** Инструмент, которым ходят кейсы. */
    protected static final String INSTRUMENT = "BTC-USDT-SWAP";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /** Наблюдатель и стаб площадки. */
    protected final ExchangeStub exchange = ExchangeStub.stub();

    /** Хранилище ключей счетов: вход кейса и наблюдатель числа чтений. */
    protected final SecretStore secrets = SecretStore.shared();

    /** Стаб провайдера идентичности: им подписываются токены кейсов. */
    protected final IdentityStub identity = IdentityStub.stub();

    /** Стаб поверхности соседа по ярусу: им наблюдается, что его не зовут. */
    protected final NeighbourStub neighbour = NeighbourStub.stub();

    /**
     * Перед каждой клеткой: стабы забывают записи, а счёт штатного
     * положения осей снабжён ключами.
     *
     * <p><b>Ключи кладутся заново, а не однажды:</b> кейсы, которым нужен
     * счёт БЕЗ ключей, убирают их из хранилища, и соседу по прогону
     * досталось бы хранилище в их состоянии.
     */
    @BeforeEach
    void resetStubs() {
        exchange.reset();
        neighbour.reset();
        secrets.put(ACCOUNT, "LIVE");
    }

    /** Тропа приватной операции счёта. */
    protected static String account(String suffix) {
        return account(ACCOUNT, suffix);
    }

    /** Тропа приватной операции названного счёта. */
    protected static String account(String accountInternalId, String suffix) {
        return "/api/v1/accounts/" + accountInternalId + suffix;
    }

    /** Тропа публичного чтения. */
    protected static String market(String suffix) {
        return "/api/v1/market" + suffix;
    }

    /** Чтение под сервисным токеном. */
    protected Answer get(String path) {
        return send(authorized(request(path)).GET());
    }

    /** Чтение без предъявленного принципала. */
    protected Answer getAnonymously(String path) {
        return send(request(path).GET());
    }

    /** Чтение под названным токеном. */
    protected Answer getWith(String path, String token) {
        return send(request(path).header("Authorization", "Bearer " + token).GET());
    }

    /** Мутирующий вызов под сервисным токеном. */
    protected Answer post(String path, String body) {
        return send(authorized(request(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Мутирующий вызов без предъявленного принципала. */
    protected Answer postAnonymously(String path, String body) {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Вызов неподдержанным методом под сервисным токеном. */
    protected Answer delete(String path) {
        return send(authorized(request(path)).DELETE());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30));
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder request) {
        return request.header("Authorization", "Bearer " + identity.serviceToken());
    }

    private static Answer send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> answer = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(answer.statusCode(), answer.body());
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
     * ТЕЛЕ — что в нём нет ключей, нет полей конверта источника, нет
     * сырого статуса площадки, — и разбор в типизованную форму такие
     * ожидания выразить не даёт.
     *
     * @param status код ответа
     * @param body   тело ответа дословно
     */
    protected record Answer(Integer status, String body) {

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
            // Тело, которое объектом не является (перечень моделей, текст
            // контейнера), формы не несёт — и это ОТВЕТ клетки, а не её
            // отказ: иначе красная клетка падала бы разбором и переставала
            // называть, чем именно ожидание не сошлось.
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

        /** Причина проблемного внешнего статуса — отдельное поле error-DTO. */
        String errorReason() {
            return String.valueOf(asObject().get("reason"));
        }
    }
}
