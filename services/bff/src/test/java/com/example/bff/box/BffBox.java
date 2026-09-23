package com.example.bff.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `bff`: реальный контекст сервиса со ВСЕМИ его бинами,
 * наблюдаемый только снаружи — своей поверхностью, проводом в браузер,
 * стабами владельцев и брокером
 * (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний
 * бин не подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по
 * адресу случайного порта, а не {@code MockMvc}. Наблюдатели субстрата
 * ({@link OwnerStub}, {@link Wire}) ходят своим соединением — ящик
 * смотрит на субстрат снаружи.
 *
 * <p><b>Базы у этого ящика нет, и «ассерт по базе» здесь не применим
 * вовсе.</b> Всё состояние периметра эфемерно — кэш членств, открытые
 * подписки, окно переигрывания, — и читается оно ТОЛЬКО поверхностью и
 * проводом (.claude/tests/cases/bff.md §«Новая ось формы»). Это не
 * послабление: состояние, наблюдаемое лишь косвенно, требует клетки,
 * которая ставит его чужим ходом и читает своим.
 *
 * <p><b>Субъект у каждой клетки СВОЙ, и это не украшение входа.</b> Кэш
 * членств живёт в памяти контекста и переживает клетку; ключ его —
 * субъект токена. Общий субъект сделал бы вход клетки зависящим от того,
 * какая клетка бежала перед ней: «к владельцу ушёл ровно один запрос»
 * было бы верно или ложно по порядку прогона. Поэтому субъект собирается
 * из имени клетки, а значение {@code S1} документа кейсов остаётся его
 * приставкой.
 *
 * <p><b>Свойств контекста здесь не объявлено ни одного, и это
 * несущее.</b> Перечень свойств задаёт КАЖДЫЙ класс кейсов своим методом
 * {@code @DynamicPropertySource}: положение осей конфигурации есть ВХОД
 * ящика, а унаследованный метод реестра перекрывал бы свои
 * переопределения в порядке, которого контракт реестра не обещает.
 * Цена названа — контекст на класс конфигурации, а не один на прогон;
 * классы, которым довольно штатного положения осей, берут его у
 * {@link SharedBffBox} и делят один контекст.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BffBox {

    /** Собственная поверхность периметра. */
    protected static final String PERIMETER = "/api/v1/bff";

    /** Контекст предъявителя: тенант и роль. */
    protected static final String CONTEXT = PERIMETER + "/context";

    /** Выдача билета подписки. */
    protected static final String TICKETS = PERIMETER + "/stream-tickets";

    /** Открытие подписки на поток. */
    protected static final String STREAM = PERIMETER + "/stream";

    /** Проба живости: единственная открытая точка контура доступа. */
    protected static final String HEALTH = "/actuator/health";

    /** Тенант, которым ходит большинство клеток. */
    protected static final String TENANT = "T1";

    /** Второй тенант: им наблюдается радиус потока и пересылки. */
    protected static final String SECOND_TENANT = "T2";

    /** Роль, которую владелец членств отдаёт единственному субъекту. */
    protected static final String ROLE = "OWNER";

    /** Заголовок контекста тенанта: тот же, что периметр шлёт владельцу. */
    protected static final String TENANT_HEADER = "X-Tenant-Id";

    /** Заголовок роли в тенанте. */
    protected static final String ROLE_HEADER = "X-Tenant-Role";

    /** Класс отказа наших собственных бросков. */
    protected static final String REQUEST_REJECTED = "PERIMETER_REQUEST_REJECTED";

    /** Класс отказа доступа: обе формы предъявления отвечают им. */
    protected static final String UNAUTHENTICATED = "ACCESS_UNAUTHENTICATED";

    /** Класс отказа недоступного владельца. */
    protected static final String PEER_UNAVAILABLE = "PEER_UNAVAILABLE";

    /** Класс отказа, произведённого контейнером. */
    protected static final String NOT_ACCEPTED = "REQUEST_NOT_ACCEPTED";

    /** Класс всего непредусмотренного. */
    protected static final String INTERNAL_FAILURE = "INTERNAL_FAILURE";

    /** Тенант зонда назначения: он не тенант ни одной клетки. */
    private static final String WARMUP_TENANT = "TW";

    /** Субъект зонда назначения: кэш членств клеток он не трогает. */
    private static final String WARMUP_SUBJECT = "SW";

    /** Такт зондирующих записей: назначение приходит за доли секунды. */
    private static final Duration PROBE_INTERVAL = Duration.ofMillis(300);

    /** Порты контекстов, чей слушатель уже наблюдён раздающим. */
    private static final Set<Integer> DELIVERING = ConcurrentHashMap.newKeySet();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /** Стаб провайдера идентичности: им подписываются токены кейсов. */
    protected final IdentityStub identity = IdentityStub.stub();

    /** Стаб и наблюдатель поверхностей шести владельцев за периметром. */
    protected final OwnerStub owners = OwnerStub.stub();

    /** Провод прогона к брокеру: вход раздачи и наблюдатель молчания. */
    protected final Wire wire = Wire.wire();

    /** Субъект этой клетки: свой у каждой — см. шапку класса. */
    protected String subject;

    /** Второй субъект этой клетки: им наблюдается, что ключ кэша — субъект. */
    protected String secondSubject;

    /**
     * Сколько записей лежало во всех темах брокера до этой клетки.
     *
     * <p>Отрицание «периметр не публикует ничего» абсолютно по предмету,
     * но брокер в прогоне ОДИН, и записи предыдущих клеток из тем никуда
     * не деваются: без отметки счёт мерил бы прогон, а не клетку.
     */
    protected Long recordsBefore;

    /** Перед каждой клеткой: слушатель раздаёт, стаб забыл, субъект свой. */
    @BeforeEach
    void resetSubstrate(TestInfo about) {
        String cell = about.getTestClass().orElseThrow().getSimpleName()
                + "-" + about.getTestMethod().orElseThrow().getName();
        subject = IdentityStub.SUBJECT + "-" + cell;
        secondSubject = IdentityStub.SECOND_SUBJECT + "-" + cell;
        if (isTrue(awaitsDelivery())) {
            awaitDelivery();
        }
        owners.reset();
        recordsBefore = wire.totalRecords();
    }

    /**
     * Дожидается, пока слушатель поднятого контекста ПОЛУЧИТ НАЗНАЧЕНИЕ
     * партиций, — однажды на контекст.
     *
     * <p><b>Без этого первая клетка свежего контекста теряет свою
     * запись, и теряет молча.</b> Позиция чтения группы — текущий момент
     * ({@code auto.offset.reset=latest}), и запись, положенная до
     * назначения партиций, не приходит никуда: клетка краснеет по
     * таймауту ожидания, то есть выглядит дефектом раздачи. Предусловие
     * «слушатель получил назначение партиций» названо самим документом
     * кейсов, и здесь оно ставится.
     *
     * <p><b>Наблюдается назначение ТОЛЬКО доехавшей записью.</b>
     * Внутренностей контекста ящик не касается, а перечень групп у
     * брокера не различает живую группу этого контекста от групп
     * контекстов, поднятых прежде: они остаются живыми до конца прогона.
     *
     * <p><b>Зонд идёт СВОИМ тенантом, и это не украшение.</b> Место под
     * потолком подписок, окно переигрывания и счёт записей ведутся по
     * тенанту: зонд, ходящий тенантом клеток, отдал бы им своё состояние
     * в наследство.
     *
     * <p><b>Ключ контекста — его порт.</b> У каждого поднятого контекста
     * он свой, и инъекция самого контекста ради различения не нужна.
     */
    /**
     * Ставит ли контекст класса предусловие «слушатель получил назначение».
     *
     * <p>Снимают его ровно те классы, чей предмет — само ОТСУТСТВИЕ
     * назначения либо состояние реплики ДО первой подписки: зонд открывает
     * подписку и ждёт доехавшей записи, то есть ставит обратное их
     * предусловию — а у контекста без брокера ещё и ждал бы потолка.
     */
    protected Boolean awaitsDelivery() {
        return Boolean.TRUE;
    }

    private void awaitDelivery() {
        if (isFalse(DELIVERING.add(port))) {
            return;
        }
        awaitDeliveryAt(port);
    }

    /**
     * Дожидается назначения партиций у реплики НАЗВАННОГО порта — без
     * отметки «уже наблюдена».
     *
     * <p>Вход клеток, поднимающих свою реплику сами: порт закрытой реплики
     * может достаться следующей, и отметка по порту пропустила бы зонд
     * ровно у той реплики, которой он нужен.
     *
     * @param replicaPort порт реплики
     */
    protected void awaitDeliveryAt(Integer replicaPort) {
        Subscription probe = deliveringProbeAt(replicaPort);
        if (Objects.nonNull(probe)) {
            probe.close();
        }
    }

    /**
     * Дожидается назначения партиций у реплики названного порта и отдаёт
     * зонд ОТКРЫТЫМ.
     *
     * <p><b>Открытым — ради клеток, чья реплика обязана остаться без
     * брошенных подписок.</b> Закрытая клиентом подписка остаётся в наборе
     * сервера до первой записи в неё, и эта запись у построенного есть
     * гонка с контейнером (находка F-14): тик пульса, рассылающий всем
     * тенантам, натыкался бы на брошенный зонд. Открытый зонд — живая
     * подписка своего тенанта, и клеткам она не мешает.
     *
     * @param replicaPort порт реплики
     * @return открытый зонд; пусто — выдача билетов у реплики не настроена
     */
    protected Subscription deliveringProbeAt(Integer replicaPort) {
        owners.answers(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH,
                Bodies.memberships(WARMUP_TENANT, ROLE));
        Answer issued = send(request(replicaPort, TICKETS)
                .header("Authorization", "Bearer " + identity.tokenFor(WARMUP_SUBJECT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("")));
        if (issued.status() != 200) {
            // Контекст, в котором выдача билетов не настроена: подписок в
            // нём не открывается ни одной, и раздачи у него нет вовсе —
            // предусловие ставить нечем и незачем. Клетки такого контекста
            // провода не касаются: их предмет — сам отказ выдачи.
            return null;
        }
        Subscription probe = subscribeAt(replicaPort, String.valueOf(issued.asObject().get("ticket")), null);
        AtomicBoolean publishing = new AtomicBoolean(Boolean.TRUE);
        Thread probes = Thread.ofVirtual().name("box-warmup").start(() -> {
            Integer attempt = 0;
            while (publishing.get()) {
                publishDealOpened(WARMUP_TENANT, "warmup-" + attempt++);
                Awaitility.await().pollDelay(PROBE_INTERVAL).atMost(PROBE_INTERVAL.plusSeconds(5))
                        .until(() -> Boolean.TRUE);
            }
        });
        try {
            probe.awaitFrames(1);
        } finally {
            publishing.set(Boolean.FALSE);
            try {
                probes.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Ожидание зонда назначения прервано", interrupted);
            }
        }
        return probe;
    }

    /** Сколько записей легло в темы с начала клетки. */
    protected Long recordsSinceStart() {
        return wire.totalRecords() - recordsBefore;
    }

    /**
     * Ставит штатный ответ владельца членств: у субъекта клетки ровно
     * одно членство.
     *
     * <p><b>Заготовка, а не предусловие в своей памяти:</b> членства
     * живут у владельца по построению, и положенные в кэш напрямую они
     * говорили бы о нашей вставке, а не о поведении периметра.
     */
    protected void authAnswersOneMembership() {
        authAnswers(Bodies.memberships(TENANT, ROLE));
    }

    /** Ставит названный ответ владельца членств. */
    protected void authAnswers(String body) {
        owners.answers(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH, body);
    }

    /**
     * Кладёт в тему ядра факт «сделка создана» названного тенанта —
     * полным конвертом.
     *
     * <p><b>Предусловие потока ставится СООБЩЕНИЕМ, и это ось предмета:</b>
     * входом раздачи является запись темы, а не вызов поверхности, —
     * обратно тому, как предусловие ставится у соседних ящиков.
     *
     * @param tenantId тенант — ключ записи и радиус раздачи
     * @param eventId  идентичность события; ею клетка узнаёт свою запись
     */
    protected void publishDealOpened(String tenantId, String eventId) {
        wire.publish(Wire.CORE_TOPIC, tenantId, eventId, "DEAL_OPENED",
                OffsetDateTime.now().toString(), Bodies.dealOpened("D-" + eventId));
    }

    /** Токен субъекта клетки, годный по всем осям. */
    protected String token() {
        return identity.tokenFor(subject);
    }

    /** Чтение под токеном субъекта клетки. */
    protected Answer get(String path) {
        return getWith(path, token());
    }

    /** Чтение под НАЗВАННЫМ токеном: вход клеток о негодных осях токена. */
    protected Answer getWith(String path, String token) {
        return send(request(path).header("Authorization", "Bearer " + token).GET());
    }

    /** Чтение БЕЗ предъявленной идентичности. */
    protected Answer getAnonymously(String path) {
        return send(request(path).GET());
    }

    /** Чтение под НАЗВАННЫМ токеном с добавленными заголовками. */
    protected Answer getWith(String path, String token, Map<String, String> headers) {
        HttpRequest.Builder builder = request(path).header("Authorization", "Bearer " + token);
        headers.forEach(builder::header);
        return send(builder.GET());
    }

    /** Мутирующий вызов под токеном субъекта клетки. */
    protected Answer post(String path, String body) {
        return postWith(path, token(), body);
    }

    /** Мутирующий вызов под НАЗВАННЫМ токеном. */
    protected Answer postWith(String path, String token, String body) {
        return send(request(path)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Мутирующий вызов под НАЗВАННЫМ токеном с названными заголовками. */
    protected Answer postWith(String path, String token, Map<String, String> headers, String body) {
        HttpRequest.Builder builder = request(path).header("Authorization", "Bearer " + token);
        headers.forEach(builder::header);
        return send(builder.POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Мутирующий вызов БЕЗ предъявленной идентичности. */
    protected Answer postAnonymously(String path, String body) {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Вызов НАЗВАННЫМ глаголом под токеном субъекта клетки. */
    protected Answer call(String method, String path, String body) {
        HttpRequest.Builder builder = request(path)
                .header("Authorization", "Bearer " + token());
        HttpRequest.BodyPublisher content = Objects.isNull(body)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        if (Objects.nonNull(body)) {
            builder = builder.header("Content-Type", "application/json");
        }
        return send(builder.method(method, content));
    }

    /**
     * Выдаёт билет подписки субъекту клетки и отдаёт его значение.
     *
     * <p><b>Тропой ящика — вызовом точки выдачи, а не сборкой значения
     * тестом:</b> билет производит сам сервис своим секретом, и
     * собранный тестом говорил бы о нашей сборке. Клетки, чей предмет —
     * билет ЧУЖОЙ сборки, собирают его сами и называют это входом.
     */
    protected String issuedTicket() {
        return issuedTicketWith(token());
    }

    /**
     * Выдаёт билет подписки предъявителю НАЗВАННОГО токена.
     *
     * <p>Вход клеток, которым нужен билет ВТОРОГО тенанта: тенант билета
     * выводится из членств предъявителя, и другого способа получить билет
     * чужого тенанта у периметра нет — что и есть предмет клетки о радиусе
     * потолка.
     *
     * @param token токен, под которым идёт выдача
     */
    protected String issuedTicketWith(String token) {
        Answer answer = postWith(TICKETS, token, "");
        if (answer.status() != 200) {
            throw new AssertionError("Предусловие не поставлено: выдача билета ответила "
                    + answer.status() + ": " + answer.body());
        }
        return String.valueOf(answer.asObject().get("ticket"));
    }

    /**
     * Открывает подписку НАЗВАННОГО тенанта и предъявляет её открытие
     * доехавшей записью.
     *
     * <p><b>Своя запись у КАЖДОЙ подписки — не избыточность, а условие
     * детерминизма.</b> Открытие асинхронно, и до первой записи о
     * регистрации подписки снаружи не известно ничего (находка F-11);
     * запись, положенная до регистрации, не приходит никуда и в окно
     * тенанта уходит молча. Клетка, открывшая две подписки и положившая
     * одну запись, зависела бы от того, чей ход успел раньше.
     *
     * @param tenantId тенант барьерной записи — он же тенант билета
     * @param ticket   билет открытия
     * @param eventId  идентичность факта-барьера
     */
    protected Subscription openedStreamOf(String tenantId, String ticket, String eventId) {
        Subscription stream = subscribe(ticket);
        publishDealOpened(tenantId, eventId);
        stream.awaitFrames(1);
        return stream;
    }

    /**
     * Открывает подписку у реплики НАЗВАННОГО порта и предъявляет её
     * открытие доехавшей записью — та же форма, что {@link #openedStreamOf}.
     *
     * @param replicaPort порт реплики
     * @param tenantId    тенант барьерной записи — он же тенант билета
     * @param ticket      билет открытия
     * @param eventId     идентичность факта-барьера
     */
    protected Subscription openedStreamAt(Integer replicaPort, String tenantId, String ticket, String eventId) {
        Subscription stream = subscribeAt(replicaPort, ticket, null);
        publishDealOpened(tenantId, eventId);
        stream.awaitFrames(1);
        return stream;
    }

    /** Открывает подписку названным билетом. */
    protected Subscription subscribe(String ticket) {
        return subscribe(ticket, null);
    }

    /** Открывает подписку названным билетом с названной позицией чтения. */
    protected Subscription subscribe(String ticket, String lastEventId) {
        return subscribeAt(port, ticket, lastEventId);
    }

    /**
     * Открывает подписку у реплики НАЗВАННОГО порта.
     *
     * @param replicaPort порт реплики
     * @param ticket      билет открытия
     * @param lastEventId позиция чтения; пусто — заголовка нет вовсе
     */
    protected Subscription subscribeAt(Integer replicaPort, String ticket, String lastEventId) {
        return subscriptionOf(streamRequest(addressOf(replicaPort,
                STREAM + "?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8)), lastEventId));
    }

    /**
     * Выдаёт билет предъявителю названного токена у реплики НАЗВАННОГО
     * порта.
     *
     * @param replicaPort порт реплики
     * @param token       токен, под которым идёт выдача
     */
    protected String issuedTicketAt(Integer replicaPort, String token) {
        Answer answer = send(request(replicaPort, TICKETS)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("")));
        if (answer.status() != 200) {
            throw new AssertionError("Предусловие не поставлено: выдача билета у реплики "
                    + replicaPort + " ответила " + answer.status() + ": " + answer.body());
        }
        return String.valueOf(answer.asObject().get("ticket"));
    }

    /**
     * Чтение под названным токеном у реплики НАЗВАННОГО порта.
     *
     * @param replicaPort порт реплики
     * @param path        путь поверхности
     * @param token       токен предъявителя
     */
    protected Answer getAt(Integer replicaPort, String path, String token) {
        return send(request(replicaPort, path).header("Authorization", "Bearer " + token).GET());
    }

    /** Открывает подписку БЕЗ параметра билета: билет не предъявлен вовсе. */
    protected Subscription subscribeWithoutTicket() {
        return subscribeTo(STREAM, null);
    }

    /**
     * Открывает подписку под BEARER-ТОКЕНОМ и без билета.
     *
     * <p>Вход клетки о том, что исключение тропы из bearer-цепочки
     * заменяет форму предъявления, а не снимает её: годный токен на этой
     * тропе добытчиком не является, и проверка билета в самой точке
     * безусловна.
     *
     * @param token токен, предъявленный на тропе потока
     */
    protected Subscription subscribeBearing(String token) {
        return subscriptionOf(HttpRequest.newBuilder()
                .uri(URI.create(addressOf(STREAM)))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + token)
                .GET().build());
    }

    /**
     * Открывает подписку по названному адресу.
     *
     * <p>Тело читается ПОТОКОМ: ответ провода не кончается, и чтение его
     * целиком висело бы до срока соединения.
     *
     * @param path        адрес подписки вместе со строкой запроса
     * @param lastEventId позиция чтения; пусто — заголовка нет вовсе
     */
    protected Subscription subscribeTo(String path, String lastEventId) {
        return subscriptionOf(streamRequest(addressOf(path), lastEventId));
    }

    private static HttpRequest streamRequest(String address, String lastEventId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(address))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "text/event-stream");
        if (Objects.nonNull(lastEventId)) {
            builder = builder.header("Last-Event-ID", lastEventId);
        }
        return builder.GET().build();
    }

    /**
     * Открывает подписку СВОИМ клиентом — по клиенту на подписку.
     *
     * <p><b>Общий клиент здесь отдаёт байты одной подписки другой.</b>
     * Провод закрывается со стороны клиента посреди незаконченного ответа,
     * а соединение возвращается в пул недочитанным: следующий запрос
     * разбирает его хвост как начало СВОЕГО ответа — и клетка видит в
     * свежей подписке запись, положенную до её открытия. Наблюдено один
     * раз на полном прогоне; свой клиент закрывается вместе с подпиской, и
     * пула, переживающего её, не остаётся.
     *
     * @param request запрос открытия подписки
     */
    private Subscription subscriptionOf(HttpRequest request) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return Subscription.of(client, client.sendAsync(request,
                HttpResponse.BodyHandlers.ofInputStream()));
    }

    /**
     * Открывает подписку и предъявляет её открытие ДОЕХАВШЕЙ записью.
     *
     * <p><b>Записью, а не кодом ответа, и это следствие построенного:</b>
     * заголовки провода уходят клиенту вместе с первой записью (находка
     * F-11 документа кейсов), поэтому «билет принят» наблюдается ровно
     * тем, что по проводу пришёл факт. Клетка, утверждавшая бы открытие
     * одним статусом, ждала бы ответа, которого без записи не бывает.
     *
     * @param ticket  билет открытия
     * @param eventId идентичность факта-барьера
     * @return открытая подписка с одной пришедшей записью
     */
    protected Subscription openedStream(String ticket, String eventId) {
        return openedStreamOf(TENANT, ticket, eventId);
    }

    /** Адрес поверхности ящика: им ходит и подписка на поток. */
    protected String addressOf(String path) {
        return addressOf(port, path);
    }

    /** Адрес поверхности реплики НАЗВАННОГО порта. */
    protected static String addressOf(Integer replicaPort, String path) {
        return "http://localhost:" + replicaPort + path;
    }

    private HttpRequest.Builder request(String path) {
        return request(port, path);
    }

    private static HttpRequest.Builder request(Integer replicaPort, String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(addressOf(replicaPort, path)))
                .timeout(Duration.ofSeconds(60));
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
     * ТЕЛЕ — что в нём нет присланного браузером значения, что причина
     * отказа неразличима, что тело пришло байтами владельца, — и разбор в
     * типизованную форму такие ожидания выразить не даёт.
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

        /** Пояснение отказа: в нём едет повод, различающий броски одного класса. */
        String errorMessage() {
            return String.valueOf(asObject().get("message"));
        }
    }
}
