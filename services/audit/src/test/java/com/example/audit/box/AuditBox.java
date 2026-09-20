package com.example.audit.box;

import com.example.audit.domain.jobs.JournalCleanupJob;
import com.example.audit.domain.jobs.ReceptionStateJob;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `audit`: реальный контекст сервиса со ВСЕМИ его бинами,
 * наблюдаемый только снаружи — своей поверхностью, базой, темой и
 * смещениями группы (.claude/decisions/test-contour-design-pass.md,
 * решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний
 * бин не подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по
 * адресу случайного порта, свой клиент брокера ({@link Wire}), своё
 * соединение к базе ({@link Rows}).
 *
 * <p><b>Касание бина одно ПО РОДУ — такт джобы, — а джоб у сервиса
 * две.</b> Ручного фасада нет ни у одной намеренно: поверхность объявлена
 * только читающей, и триггер завёл бы входящую точку записи
 * (docs/components/ReceptionStateJob.md §«Форма — джоба без ручного
 * фасада, и это объявлено»; docs/components/JournalCleanupJob.md §«Форма —
 * джоба без ручного фасада, и это объявлено»). Решение 6 называет прямой
 * вызов метода джобы единственной такой точкой у ящика уровня 1, и
 * расписание обеих гасится осями конфигурации, а не подменой бина
 * (.claude/tests/cases/audit.md §«Чем достаются выходы»).
 *
 * <p><b>ВХОД у этого предмета есть ЗАПИСЬ БРОКЕРА, а не запрос.</b>
 * Поверхность здесь — наблюдатель выхода наравне с базой, а не податель
 * входа: команд у сервиса нет по инвентарю
 * (.claude/tests/cases/audit.md §«Новая ось формы — событие как ВХОД»).
 *
 * <p><b>Асинхронный след ждётся СМЕЩЕНИЕМ, а не только строкой.</b>
 * «Строки нет» у отравленного сообщения недостаточно — тот же исход дал
 * бы молчаливый пропуск, — поэтому конец обработки наблюдается тем, что
 * зафиксированное группой смещение догнало конец темы.
 *
 * <p><b>База опустошается перед каждой клеткой.</b> Вход клетки есть её
 * собственное состояние журнала и строк приёма, и наследство соседки
 * сделало бы отрицания («второй строки нет», «пара не двигалась»)
 * утверждениями о прогоне, а не о клетке.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AuditBox {

    /** Журнальная выборка чтения: единственная точка предмета. */
    protected static final String JOURNAL_RECORDS = "/api/v1/audit/journal/records";

    /** Проба живости — первая открытая точка актуатора. */
    protected static final String LIVENESS_PROBE = "/actuator/health";

    /** Съём рядов — вторая открытая точка актуатора. */
    protected static final String METRICS_SCRAPE = "/actuator/prometheus";

    /** Описание объявленной поверхности: им читается состав маршрутов. */
    protected static final String SURFACE_DESCRIPTION = "/v3/api-docs";

    /**
     * Корень актуатора: он отдаёт ссылки на ПЕРЕЧЕНЬ экспозиции.
     *
     * <p>Им читается сам перечень, а не отдельные его имена: перебор точек
     * доказывает отсутствие названных, а равенство перечня двум объявленным
     * именам — только выдача корня.
     */
    protected static final String ACTUATOR_ROOT = "/actuator";

    /** Тенант, которым ходит большинство кейсов. */
    protected static final String TENANT = "T1";

    /** Второй тенант: им наблюдается радиус чтения. */
    protected static final String SECOND_TENANT = "T2";

    /** Заголовок контекста тенанта: тот же, что читает поверхность. */
    protected static final String TENANT_HEADER = "X-Tenant-Id";

    /** Таблица журнала: её строки читает прямой ассерт по колонкам. */
    protected static final String JOURNAL_TABLE = "audit_records";

    /** Таблица состояния приёма: поверхности у неё нет вовсе. */
    protected static final String RECEPTION_TABLE = "reception_states";

    /**
     * Таблица следа отвергнутого по правам вызова.
     *
     * <p>Лежит в той же базе, что журнал, а глубины хранения у неё нет:
     * журнальную ей не приписывают — та выведена из другого вопроса
     * (docs/components/JournalCleanupJob.md §Границы).
     */
    protected static final String DENIALS_TABLE = "access_denials";

    /** Колонка флага остановки приёма пары. */
    protected static final String HALTED_COLUMN = "reception_halted";

    /** Колонка момента последнего принятого события пары. */
    protected static final String LAST_ACCEPTED_COLUMN = "last_accepted_occurred_at";

    /** Колонка момента разрыва в смещениях пары. */
    protected static final String GAP_COLUMN = "lag_gap_at";

    /** Колонка момента последнего такта тика: её писатель — только тик. */
    protected static final String UPDATED_COLUMN = "updated_at";

    /** Колонка момента происшествия строки журнала: ось производства. */
    protected static final String OCCURRED_COLUMN = "occurred_at";

    /**
     * Колонка момента приёма строки журнала.
     *
     * <p>Ось приёма — та, по которой считается первый операнд нижней
     * границы полноты и глубина чистки
     * (docs/models/domain/other/AuditRecord.md §«Глубина хранения»).
     */
    protected static final String RECORDED_COLUMN = "recorded_at";

    /** Колонка момента, с которого группа наблюдает тему непрерывно. */
    protected static final String OBSERVED_COLUMN = "observed_since";

    /** Колонка признака подписки: истина теме подписки, ложь ушедшей. */
    protected static final String SUBSCRIBED_COLUMN = "subscribed";

    /** Колонка имени группы — первой половины ключа пары. */
    protected static final String GROUP_COLUMN = "consumer_group";

    /** Колонка темы — второй половины ключа пары. */
    protected static final String TOPIC_COLUMN = "topic";

    /**
     * Ряд возраста последнего принятого события пары.
     *
     * <p><b>Имена рядов написаны формой ЭКСПОЗИЦИИ, а не формой реестра</b>
     * ({@code Constants.ReceptionMetrics}): наблюдатель окружения читает
     * выдачу, и точки имени в ней уже заменены подчёркиваниями. Литерал
     * здесь и есть то, что увидит правило алерта.
     */
    protected static final String AGE_ROW = "audit_journal_reception_last_event_age_ms";

    /** Ряд порога алерта на лаг пары — доли срока хранения её темы. */
    protected static final String THRESHOLD_ROW = "audit_journal_reception_lag_alert_threshold_ms";

    /** Ряд остатка непринятого по паре. */
    protected static final String UNCONSUMED_ROW = "audit_journal_reception_unconsumed_records";

    /** Имя заголовка с идентичностью события. */
    protected static final String EVENT_ID = "eventId";

    /** Имя заголовка с классом события. */
    protected static final String EVENT_TYPE = "eventType";

    /** Имя заголовка с моментом происшествия. */
    protected static final String OCCURRED_AT = "occurredAt";

    /** Имя заголовка с версией формы содержимого. */
    protected static final String VERSION = "version";

    /** Имя заголовка с контекстом трассировки. */
    protected static final String TRACE_CONTEXT = "traceContext";

    /** Класс события, которым ходит большинство кейсов. */
    protected static final String DEAL_OPENED = "DEAL_OPENED";

    /** Версия формы содержимого штатного прогона. */
    protected static final String FORM_VERSION = "1";

    /**
     * Потолок ожидания асинхронного следа приёма.
     *
     * <p>Ограничен, потому что бесконечное ожидание висело бы ровно там,
     * где приём не идёт; «не упало» проверкой не является.
     */
    protected static final Duration RECEPTION_WAIT = Duration.ofSeconds(60);

    /** Шаг опроса у всякого ожидания ящика. */
    protected static final Duration POLL = Duration.ofMillis(200);

    /** Вставка строки пары: колонки те же, которыми её ведут её писатели. */
    private static final String INSERT_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted,
                 lag_gap_at, last_accepted_occurred_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /**
     * Имя группы потребителя ЭТОГО контекста.
     *
     * <p><b>Читается из свойства, а не из константы субстрата:</b> у класса
     * со своим положением осей группа своя
     * ({@link AuditSubstrate#registerOwn}), и зашитая константа читала бы
     * смещения чужой группы — то есть наблюдала бы не тот предмет.
     */
    @Value("${" + AuditSubstrate.CONSUMER_GROUP_KEY + "}")
    private String consumerGroup;

    /** Объявленная подписка этого контекста: из неё выводится число пар. */
    @Value("${" + AuditSubstrate.TOPICS_KEY + "}")
    private String subscription;

    /**
     * Тик состояния приёма — первая из двух джоб, такт которых ящик
     * подаёт сам. Довод и его дом — в шапке класса.
     */
    @Autowired
    private ReceptionStateJob receptionStateJob;

    /**
     * Чистка журнала — вторая такая джоба.
     *
     * <p><b>Её такт тоже ВХОД, а не подмена.</b> Проход чистки есть
     * единственный писатель, двигающий первый операнд нижней границы
     * полноты, и наблюдать его иначе нечем: расписание у неё выражением
     * CRON, до которого прогон не доживает
     * (docs/components/JournalCleanupJob.md §«Чистка границу не ломает, а
     * двигает»).
     */
    @Autowired
    private JournalCleanupJob journalCleanupJob;

    /** Наблюдатель строк базы субстрата. */
    protected final Rows rows = Rows.shared();

    /** Стаб провайдера идентичности: им подписываются токены кейсов. */
    protected final IdentityStub identity = IdentityStub.stub();

    /** Перед каждой клеткой: база пуста. */
    @BeforeEach
    void resetSubstrate() {
        rows.clear();
    }

    /**
     * Подаёт такт тика — до тех пор, пока строки обеих пар не заведены.
     *
     * <p><b>Повторяется он не «на всякий случай», а по свойству
     * предмета.</b> Тик молчит, пока приём не жив, а живость мерится
     * НАЗНАЧЕННЫМИ партициями: сразу после подъёма контекста назначение
     * ещё не состоялось, и первый такт законно не пишет ничего. Один
     * такт, поданный в этот момент, делал бы предусловие клетки гонкой с
     * ребалансировкой группы.
     */
    protected void givenReceptionStateRows() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> {
                    receptionStateJob.tick();
                    return Objects.equals(rows.count(RECEPTION_TABLE), (long) subscription().size());
                });
    }

    /** Один такт тика — им клетка двигает величины, чей писатель он. */
    protected void tick() {
        receptionStateJob.tick();
    }

    /**
     * Один проход чистки журнала.
     *
     * <p>Применимость прохода решает ось окружения, а глубину — величина
     * конфигурации: клетка сдвигает не их, а ВОЗРАСТ строк
     * ({@link #recordedEarlier}). Часы процесса не двигаются ни в одном
     * кейсе (.claude/tests/cases/audit.md §«Чем достаются выходы»).
     */
    protected void cleanup() {
        journalCleanupJob.tick();
    }

    /**
     * Ставит момент приёма названной строки журнала.
     *
     * <p><b>Это durable-ВХОД клетки, а не подмена её выхода.</b> Глубина
     * чистки назначается сутками, а прогон живёт секунды: строки, принятой
     * раньше глубины, тропа ящика не производит ни при какой расстановке —
     * её даёт только возраст, поставленный в данных. Форма строки при этом
     * объявлена домом и читается наружу
     * (docs/models/domain/other/AuditRecord.md §Персистентность), поэтому
     * запись по колонке говорит о том же, о чём читает ассерт.
     *
     * @param eventId идентичность события, чья строка стареет
     * @param moment  момент приёма, который строка получает
     */
    protected void recordedEarlier(String eventId, OffsetDateTime moment) {
        rows.write("update " + JOURNAL_TABLE + " set " + RECORDED_COLUMN + " = ? where event_id = ?",
                moment, eventId);
    }

    /**
     * Ставит момент обновления строки названной пары.
     *
     * <p><b>Это durable-ВХОД клетки, а не подмена её выхода.</b> Третий
     * конъюнкт предиката непрерывности — свежесть самой строки состояния
     * (docs/spec/durable-reception.json, {@code receptionStateFresh}), и
     * состарить строку тропой ящика нечем: единственный её писатель — тик,
     * а он ставит момент СВОЕГО такта, то есть «сейчас». Форма строки при
     * этом объявлена домом и читается наружу
     * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
     * таблица `reception_states`»), поэтому запись по колонке говорит о
     * том же, о чём читает ассерт. Часы процесса не двигаются ни в одном
     * кейсе (.claude/tests/cases/audit.md §«Чем достаются выходы»).
     *
     * @param topic  тема пары, чья строка стареет или свежеет
     * @param moment момент обновления, который строка получает
     */
    protected void pairUpdatedAt(String topic, OffsetDateTime moment) {
        rows.write("update " + RECEPTION_TABLE + " set " + UPDATED_COLUMN + " = ?"
                + " where " + GROUP_COLUMN + " = ? and " + TOPIC_COLUMN + " = ?",
                moment, consumerGroup(), topic);
    }

    /**
     * Заводит строку состояния пары прямой записью — со всеми её
     * колонками.
     *
     * <p><b>Это durable-ВХОД клетки, а не подмена её выхода.</b> Тропой
     * ящика не производятся три из них: момент разрыва пишет обнаружение
     * по смещениям — то есть состояние группы НА БРОКЕРЕ, которое своему
     * классу и принадлежит; момент наблюдения тик ставит своим тактом, то
     * есть «сейчас», а клетке о движении нижней границы нужен момент
     * ПОЗАДИ моментов приёма; флаг остановки ставит отказ обработки,
     * занимающий единственный поток слушателя до конца прогона. Форма
     * строки при этом объявлена домом и читается наружу
     * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма
     * — таблица `reception_states`»), поэтому запись по колонкам говорит
     * о том же, о чём читает ассерт.
     *
     * <p><b>Момент обновления ставится «сейчас», и это не умолчание для
     * краткости:</b> свежесть строки есть третий конъюнкт предиката
     * непрерывности, и оставленная старой строка роняла бы предикат по
     * поводу, которого клетка не ставила. Кому нужен иной возраст, двигает
     * его {@link #pairUpdatedAt}.
     *
     * @param topic         тема пары
     * @param observedSince момент, с которого группа наблюдает тему
     * @param subscribed    тема сейчас в подписке группы
     * @param halted        приём по паре остановлен
     * @param gapAt         момент обнаружения разрыва; пусто — разрыва не было
     * @param lastAccepted  момент происшествия последнего принятого события
     */
    protected void givenPair(String topic, OffsetDateTime observedSince, Boolean subscribed,
                             Boolean halted, OffsetDateTime gapAt, OffsetDateTime lastAccepted) {
        rows.write(INSERT_PAIR, consumerGroup(), topic, observedSince, subscribed, halted,
                gapAt, lastAccepted, now());
    }

    /**
     * Момент обновления, СТАРШЕ допустимого возраста ровно на секунду.
     *
     * <p>Свежесть объявлена как «возраст МЕНЬШЕ допустимого», поэтому
     * граница читается с двух сторон: этот момент строку свежей уже не
     * оставляет, соседний ({@link #freshMoment()}) — ещё оставляет.
     */
    protected static OffsetDateTime staleMoment() {
        return momentsAgo(AuditSubstrate.STATE_MAX_AGE.plusSeconds(1));
    }

    /** Момент обновления, МОЛОЖЕ допустимого возраста ровно на секунду. */
    protected static OffsetDateTime freshMoment() {
        return momentsAgo(AuditSubstrate.STATE_MAX_AGE.minusSeconds(1));
    }

    /** Имя группы потребителя этого контекста. */
    protected String consumerGroup() {
        return consumerGroup;
    }

    /** Темы объявленной подписки этого контекста в порядке записи. */
    protected List<String> subscription() {
        return List.of(subscription.split(","));
    }

    /**
     * Кладёт в тему запись с ПОЛНЫМ конвертом.
     *
     * @param topic      тема производителя
     * @param eventId    идентичность события
     * @param occurredAt момент происшествия
     * @param content    содержимое дословно
     */
    protected void publish(String topic, String eventId, OffsetDateTime occurredAt, String content) {
        Wire.publish(topic, TENANT, envelope(eventId, occurredAt), content);
    }

    /**
     * Полный конверт названного события: пять заголовков, которые ставит
     * построенный публикатор.
     *
     * @param eventId    идентичность события
     * @param occurredAt момент происшествия
     */
    protected static Map<String, String> envelope(String eventId, OffsetDateTime occurredAt) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EVENT_ID, eventId);
        headers.put(EVENT_TYPE, DEAL_OPENED);
        headers.put(OCCURRED_AT, occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        headers.put(VERSION, FORM_VERSION);
        headers.put(TRACE_CONTEXT, "00-" + eventId + "-0000000000000001-01");
        return headers;
    }

    /**
     * Ждёт, пока группа зафиксирует смещение конца названной темы.
     *
     * <p><b>Это и есть наблюдаемый конец обработки.</b> Строка журнала
     * говорит только о принятом; равенство смещений говорит, что
     * обработано ВСЁ положенное — включая записи, которых строка не
     * оставляет (повтор, поглощённый ключом дедупа).
     *
     * @param topic тема, по которой ждётся продвижение
     */
    protected void awaitConsumed(String topic) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Wire.endOffset(topic),
                        Wire.committedOffset(consumerGroup, topic)));
    }

    /**
     * Ждёт, пока в журнале окажется названное число строк.
     *
     * <p><b>Нужен там, где конец обработки смещением не наблюдается.</b>
     * Управляющая запись транзакции занимает своё смещение и потребителю
     * не отдаётся ({@link Wire#publishInTransaction}), поэтому равенство
     * «конец темы = зафиксированное смещение» на такой теме не наступает
     * вовсе, и {@link #awaitConsumed} истекал бы по таймауту при исправной
     * системе.
     *
     * @param expected сколько строк journal обязан нести
     */
    protected void awaitRecordCount(Long expected) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(JOURNAL_TABLE), expected));
    }

    /**
     * Подаёт НАЗНАЧЕНИЕ ПАРТИЦИЙ — вход первого момента обнаружения
     * разрыва (docs/rules/durable-consumer-reception.md §«Обнаружение
     * разрыва — сравнение смещений, и моментов у него два»).
     *
     * <p><b>У поднятого контекста назначение случается один раз — раньше,
     * чем есть куда писать.</b> Строки пар заводит тик, а он молчит, пока
     * приём не жив; к моменту первого назначения строк нет, и обновления
     * слушателя цели не находят — верный исход, ограниченный одним тактом
     * тика (docs/rules/durable-consumer-reception.md §«Строки пары ещё нет
     * — не пишется ничего»). Чтобы клетка наблюдала сравнение смещений, её
     * назначение обязано случиться ПОСЛЕ такта, и подаётся оно снаружи —
     * на брокере, без касания второго бина ({@link Wire#reassignPartitions}).
     */
    protected void reassignPartitions() {
        Wire.reassignPartitions(consumerGroup, subscription());
    }

    /** Строки журнала в порядке записи. */
    protected List<Map<String, Object>> records() {
        return rows.all(JOURNAL_TABLE);
    }

    /** Единственная строка журнала; иное число — падение. */
    protected Map<String, Object> record() {
        List<Map<String, Object>> found = records();
        if (found.size() != 1) {
            throw new AssertionError("Ожидалась ровно одна строка журнала, их " + found.size());
        }
        return found.getFirst();
    }

    /** Строка состояния приёма по названной теме. */
    protected Map<String, Object> pair(String topic) {
        return rows.row(RECEPTION_TABLE, TOPIC_COLUMN, topic);
    }

    /** Все строки состояния приёма в порядке записи. */
    protected List<Map<String, Object>> pairs() {
        return rows.all(RECEPTION_TABLE);
    }

    /**
     * Объявленная полнота, едущая с выдачей чтения: утверждаема ли
     * непрерывность.
     *
     * <p><b>Обе величины читаются ЧЕРЕЗ ПОВЕРХНОСТЬ</b>, а не считаются
     * тестом по колонкам: их предмет — что журнал объявляет о себе
     * читателю (docs/rules/durable-consumer-reception.md §«Величины едут
     * рядом с числами, а не отдельным запросом»).
     */
    protected Object continuityClaimable() {
        return completeness().get("continuityClaimable");
    }

    /** Нижняя граница полноты, едущая с выдачей чтения. */
    protected Object lowerBound() {
        return completeness().get("lowerBound");
    }

    /**
     * Нижняя граница полноты как точка шкалы; её отсутствие — падение.
     *
     * <p>Читается она ЧЕРЕЗ ПОВЕРХНОСТЬ — предмет величины в том, что
     * журнал объявляет о себе читателю, — а разбирается здесь потому, что
     * часть клеток утверждает о её ЗНАЧЕНИИ и о направлении её движения, а
     * не о самом факте правки.
     */
    protected OffsetDateTime lowerBoundMoment() {
        Object value = lowerBound();
        if (Objects.isNull(value)) {
            throw new AssertionError("Нижняя граница полноты отсутствует: обещать нечего");
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private Map<String, Object> completeness() {
        return journal(TENANT, momentsAgo(Duration.ofHours(1)), now()).completeness();
    }

    /** Версия строки состояния названной пары: ею видно повторную запись. */
    protected String pairVersion(String topic) {
        return rows.rowVersion(RECEPTION_TABLE, "topic", topic);
    }

    /**
     * Страница журнала тенанта за названное окно.
     *
     * @param tenantInternalId тенант заголовка контекста
     * @param from             левая граница окна по моменту происшествия
     * @param to               правая граница того же окна
     */
    protected Answer journal(String tenantInternalId, OffsetDateTime from, OffsetDateTime to) {
        return get(journalPath(from, to), tenantInternalId);
    }

    /**
     * Путь страницы журнала: окно, затем операнды парами «имя, значение».
     *
     * <p><b>Дом формы вопроса один на оба класса группы чтения:</b> у клетки
     * со своими осями конфигурации вопрос тот же, и вторая запись его формы
     * разошлась бы с первой первой же правкой
     * (.claude/rules/carrier-levels.md).
     *
     * @param from     левая граница окна по моменту происшествия
     * @param to       правая граница того же окна
     * @param operands пары «имя операнда, его значение» сверх окна
     */
    protected static String journalPath(OffsetDateTime from, OffsetDateTime to, String... operands) {
        StringBuilder path = new StringBuilder(JOURNAL_RECORDS)
                .append("?from=").append(moment(from))
                .append("&to=").append(moment(to));
        for (int index = 0; index < operands.length; index += 2) {
            path.append("&").append(operands[index]).append("=")
                    .append(URLEncoder.encode(operands[index + 1], StandardCharsets.UTF_8));
        }
        return path.toString();
    }

    /**
     * Момент в форме параметра запроса.
     *
     * <p>Он несёт знак смещения и двоеточия, и незакодированным приехал бы
     * разобранным иначе — то есть клетка мерила бы разбор URI, а не
     * поведение выборки.
     */
    protected static String moment(OffsetDateTime value) {
        return URLEncoder.encode(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                StandardCharsets.UTF_8);
    }

    /** Момент «сейчас» на шкале UTC — единственной шкале проекта. */
    protected static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Момент в прошлом.
     *
     * <p><b>Усечён до миллисекунд намеренно:</b> колонка хранит момент с
     * микросекундной точностью, а конверт едет текстом — и без усечения
     * сравнение мерило бы округление форматирования, а не запись.
     *
     * @param back насколько назад от «сейчас»
     */
    protected static OffsetDateTime momentsAgo(Duration back) {
        return now().minus(back).truncatedTo(ChronoUnit.MILLIS);
    }

    /** Момент колонки как точка шкалы: смещение записи контракта не несёт. */
    protected static Instant instant(Map<String, Object> row, String column) {
        return ((OffsetDateTime) row.get(column)).toInstant();
    }

    /**
     * Значение ряда возраста последнего принятого события по теме; пусто
     * — ряда нет.
     *
     * @param topic тема пары
     */
    protected Long ageRowOf(String topic) {
        return metricRowOf(AGE_ROW, topic);
    }

    /** Значение ряда порога алерта по теме; пусто — ряда нет. */
    protected Long thresholdRowOf(String topic) {
        return metricRowOf(THRESHOLD_ROW, topic);
    }

    /**
     * Значение названного ряда по теме; пусто — ряда нет.
     *
     * <p>Снимается ЭКСПОЗИЦИЕЙ, а не реестром: наблюдатель окружения
     * читает именно её, и ряд, живущий только в реестре, до правила
     * алерта не доезжает.
     *
     * <p><b>Пустота здесь означает «ряда нет», а не «ноль»</b>, и это
     * несущее различение: неизмеренная величина ряда не получает вовсе, а
     * поданная числом молча решила бы исход правила
     * (docs/components/ReceptionStateJob.md §«Неизмеренная величина уносит
     * СВОЙ ряд, а не подменяется числом»).
     *
     * @param metric имя ряда в выдаче экспозиции
     * @param topic  тема пары
     */
    protected Long metricRowOf(String metric, String topic) {
        String marker = metric + "{topic=\"" + topic + "\"}";
        return scrape().lines()
                .filter(line -> line.startsWith(marker))
                .map(line -> (long) Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Сколько рядов приёма несёт выдача — по всем трём величинам и всем
     * парам разом.
     *
     * <p>Им выражается отрицание «рядов не осталось ни одного»: такт,
     * который не измерил, уносит их ВСЕ, а не обнуляет.
     */
    protected Long receptionRowCount() {
        return scrape().lines()
                .filter(line -> line.startsWith(AGE_ROW)
                        || line.startsWith(THRESHOLD_ROW)
                        || line.startsWith(UNCONSUMED_ROW))
                .count();
    }

    /** Выдача экспозиции дословно. */
    private String scrape() {
        return get(METRICS_SCRAPE, TENANT).body();
    }

    /** Чтение под сервисным токеном и контекстом названного тенанта. */
    protected Answer get(String path, String tenantInternalId) {
        return send(request(path)
                .header("Authorization", "Bearer " + identity.serviceToken())
                .header(TENANT_HEADER, tenantInternalId)
                .GET());
    }

    /**
     * Чтение под сервисным токеном, БЕЗ заголовка контекста тенанта.
     *
     * <p><b>Им наблюдается отказ, который производит КОНТЕЙНЕР, а не наш
     * код:</b> обязательность заголовка выражена контрактом точки, и класс
     * такого отказа другой — форма вызова отвергается раньше, чем вопрос
     * чтения доходит до выборки
     * (.claude/tests/cases/audit.md §«Число ответа и класс отказа — разные
     * ожидания»).
     *
     * @param path путь поверхности вместе с операндами запроса
     */
    protected Answer getWithoutTenant(String path) {
        return send(request(path)
                .header("Authorization", "Bearer " + identity.serviceToken())
                .GET());
    }

    /**
     * Чтение БЕЗ предъявленной идентичности: заголовок контекста есть,
     * заголовка авторизации нет.
     *
     * <p><b>Контекст подаётся намеренно:</b> клетка о закрытом умолчании
     * утверждает, что вызов отвергает КОНТУР, а не разбор контекста
     * тенанта, — а контейнер отвергает непредъявленный заголовок своим
     * классом (.claude/tests/cases/audit.md §«Число ответа и класс отказа
     * — разные ожидания»). Без заголовка два отказа стали бы неразличимы
     * по поводу, совпав по коду.
     *
     * @param path путь поверхности вместе с операндами запроса
     */
    protected Answer getAnonymously(String path) {
        return send(request(path).header(TENANT_HEADER, TENANT).GET());
    }

    /**
     * Чтение под НАЗВАННЫМ токеном: вход клеток о негодных осях токена.
     *
     * @param path  путь поверхности
     * @param token токен, который предъявляет клетка
     */
    protected Answer getWith(String path, String token) {
        return send(request(path)
                .header("Authorization", "Bearer " + token)
                .header(TENANT_HEADER, TENANT)
                .GET());
    }

    /**
     * Вызов названным методом под предъявленным принципалом.
     *
     * <p><b>Им проверяются ОТРИЦАНИЯ поверхности.</b> Точка, которой нет,
     * обязана отвечать «нет такой точки» — а не отказом доступа: иначе
     * «не построено» было бы неотличимо от «построено и закрыто», и
     * утверждение «входящей точки записи нет» держалось бы на контуре, а
     * не на инвентаре (.claude/tests/cases/audit.md §«Поверхность
     * предмета»).
     *
     * @param method метод запроса
     * @param path   путь поверхности
     */
    protected Answer call(String method, String path) {
        return send(request(path)
                .header("Authorization", "Bearer " + identity.serviceToken())
                .header(TENANT_HEADER, TENANT)
                .method(method, HttpRequest.BodyPublishers.noBody()));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60));
    }

    private static Answer send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> answer = CLIENT.send(request.build(),
                    HttpResponse.BodyHandlers.ofString());
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
     * ТЕЛЕ — что содержимое едет объектом, а не строкой с экранированием,
     * — и разбор в типизованную форму такое ожидание выразить не даёт.
     *
     * @param status  код ответа
     * @param body    тело ответа дословно
     * @param headers заголовки ответа по именам
     */
    protected record Answer(Integer status, String body, Map<String, List<String>> headers) {

        /** Тело как объект. */
        Map<String, Object> asObject() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }

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

        /**
         * Несёт ли тело единый error-DTO поверхности: класс отказа и
         * момент.
         *
         * <p>Форма читается по ПОЛЯМ, а не по коду ответа: отказ
         * контейнера отдаёт то же тело при своём статусе, а умолчание
         * ресурс-сервера отвечает ПУСТЫМ телом — то есть вторым форматом,
         * существование которого клейм «единый DTO» и отрицает.
         *
         * <p><b>Неразобранное тело даёт ОТВЕТ, а не отказ клетки:</b>
         * иначе красная клетка падала бы разбором и переставала называть,
         * чем ожидание не сошлось.
         */
        Boolean carriesErrorDto() {
            if (Objects.isNull(body) || body.isBlank()) {
                return Boolean.FALSE;
            }
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

        /** Пояснение отказа: им назван повод, а не внутреннее устройство. */
        String errorMessage() {
            return String.valueOf(asObject().get("message"));
        }

        /** Строки страницы. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records() {
            return (List<Map<String, Object>>) asObject().get("records");
        }

        /** Объявленная полнота, едущая с каждой выдачей чисел. */
        @SuppressWarnings("unchecked")
        Map<String, Object> completeness() {
            return (Map<String, Object>) asObject().get("completeness");
        }

        /**
         * Позиция продолжения страницы; пусто — окно дочитано до конца.
         *
         * <p>Пустота здесь ЗНАЧЕНИЕ, а не неизвестность, поэтому клетка
         * читает её как есть — и отличает от пары, у которой обе половины
         * названы.
         */
        @SuppressWarnings("unchecked")
        Map<String, Object> nextCursor() {
            return (Map<String, Object>) asObject().get("nextCursor");
        }
    }
}
