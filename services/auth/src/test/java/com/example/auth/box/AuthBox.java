package com.example.auth.box;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `auth`: реальный контекст сервиса со ВСЕМИ его бинами,
 * наблюдаемый только снаружи — поверхностью, базой и хранилищем
 * (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний
 * бин не подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по
 * адресу случайного порта, а не {@code MockMvc} и не автовайренный бин
 * сервиса. Наблюдатели субстрата ({@link Rows}, {@link SecretStore})
 * ходят своим соединением к контейнеру — ящик смотрит на субстрат
 * снаружи.
 *
 * <p><b>Свойств контекста здесь не объявлено ни одного, и это несущее.</b>
 * Перечень свойств задаёт КАЖДЫЙ класс кейсов своим методом
 * {@code @DynamicPropertySource}: положение осей окружения есть вход
 * ящика, а унаследованный метод реестра свойств перекрывал бы свои
 * переопределения в порядке, которого контракт реестра не обещает.
 * Цена названа — контекст на класс конфигурации, а не один на прогон;
 * классы, которым довольно штатного положения осей, берут его
 * {@link SharedAuthBox} и делят один контекст.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AuthBox {

    /** Тропа резолва членств предъявителя. */
    protected static final String MEMBERSHIPS_SELF = "/api/v1/auth/memberships/self";

    /** Тропа реестра биржевых счетов. */
    protected static final String EXCHANGE_ACCOUNTS = "/api/v1/auth/exchange-accounts";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /** Наблюдатель строк общего субстрата. */
    protected final Rows rows = Rows.shared();

    /** Наблюдатель общего хранилища секретов. */
    protected final SecretStore secrets = SecretStore.shared();

    /** Стаб провайдера идентичности: им подписываются токены кейсов. */
    protected final IdentityStub identity = IdentityStub.stub();

    /**
     * Заводит тенанта тропой поверхности и отдаёт его идентичность.
     *
     * <p><b>Предусловие ставится тропой ящика, а не вставкой в базу:</b>
     * иначе кейс опирался бы на строку, которой сервис не производил, и
     * зелёный прогон говорил бы о нашей вставке, а не о его поведении.
     *
     * @param subject идентификатор пользователя у провайдера — свой у
     *                каждого кейса, чтобы предусловие не зависело от
     *                соседей по прогону
     * @return {@code internalId} заведённого тенанта
     */
    protected String provisionTenant(String subject) {
        Answer answer = post(MEMBERSHIPS_SELF, identity.browserToken(subject), "");
        if (answer.status() != 200) {
            throw new IllegalStateException("Предусловие не поставлено: заведение тенанта ответило "
                    + answer.status() + " " + answer.body());
        }
        return String.valueOf(answer.single().get("tenantId"));
    }

    /** Запрос без предъявленного принципала. */
    protected Answer get(String path) {
        return send(request(path).GET());
    }

    /** Запрос под предъявленным токеном. */
    protected Answer get(String path, String token) {
        return send(authorized(request(path), token).GET());
    }

    /** Мутирующий запрос без предъявленного принципала. */
    protected Answer post(String path, String body) {
        return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Мутирующий запрос под предъявленным токеном. */
    protected Answer post(String path, String token, String body) {
        return send(authorized(request(path), token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /**
     * Запрос с дословным значением заголовка предъявления: им подаются
     * оси резолвера заголовка, которых форма {@code Bearer <токен>} не
     * выражает.
     *
     * @param path            тропа поверхности
     * @param headerValue     дословное значение заголовка
     * @return ответ поверхности
     */
    protected Answer getWithRawAuthorization(String path, String headerValue) {
        return send(request(path).header("Authorization", headerValue).GET());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30));
    }

    private static HttpRequest.Builder authorized(HttpRequest.Builder request, String token) {
        return request.header("Authorization", "Bearer " + token);
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
     * ТЕЛЕ — что в нём нет ключей, нет числового ключа базы, нет текста
     * платформенного исключения, — и разбор в типизованную форму такие
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
            Map<String, Object> parsed = asObject();
            return parsed.containsKey("code") && parsed.containsKey("occurredAt");
        }

        /** Класс отказа единого error-DTO. */
        String errorCode() {
            return String.valueOf(asObject().get("code"));
        }
    }
}
