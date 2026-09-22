package com.example.statistics.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.statistics.domain.jobs.AggregateRecomputeJob;
import com.example.statistics.domain.jobs.ReceptionStateJob;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `statistics`: реальный контекст сервиса со ВСЕМИ его бинами,
 * наблюдаемый только снаружи — своей поверхностью, базой, темой и смещениями
 * группы (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний бин не
 * подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по адресу
 * случайного порта, свой клиент брокера ({@link Wire}), своё соединение к
 * базе ({@link Rows}).
 *
 * <p><b>Касание бина одно ПО РОДУ — такт джобы, — а джоб у сервиса две.</b>
 * Ручного фасада нет ни у одной намеренно: поверхность объявлена только
 * читающей, и триггер завёл бы входящую точку записи
 * (docs/architecture/services/statistics.md §«Какие вызовы делает и какие
 * принимает»). Решение 6 называет прямой вызов метода джобы единственной
 * такой точкой у ящика уровня 1, и расписание обеих гасится осями
 * конфигурации, а не подменой бина.
 *
 * <p><b>У события ДВА следствия, разнесённые во времени разными
 * исполнителями, и это правит форму ожиданий.</b> Приём кладёт факт; строка
 * агрегата собирается ПОЗЖЕ, отдельным тактом, из фактов целиком
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»). Поэтому появление строки агрегата асинхронным следом не
 * является — его производит такт, который подаёт сам тест, — а появление
 * факта является, и ждётся оно смещением.
 *
 * <p><b>Асинхронный след ждётся СМЕЩЕНИЕМ, а не только строкой.</b> «Строки
 * нет» у события ненесомого класса недостаточно — тот же исход дал бы
 * молчаливый пропуск, — поэтому конец обработки наблюдается тем, что
 * зафиксированное группой смещение догнало конец темы.
 *
 * <p><b>Сутки зерна выбираются МОМЕНТОМ события, а не ожиданием полуночи</b>
 * ({@link #midnightDaysAgo}); часы процесса не двигаются ни в одном кейсе
 * (.claude/tests/cases/statistics.md §«Чем достаются выходы»).
 *
 * <p><b>База опустошается перед каждой клеткой.</b> Вход клетки есть её
 * собственное состояние фактов, агрегатов и строк приёма, и наследство
 * соседки сделало бы отрицания («второй строки нет», «пара не двигалась»)
 * утверждениями о прогоне, а не о клетке.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class StatisticsBox {

    /** Агрегатная выборка чтения: единственная точка предмета. */
    protected static final String AGGREGATE_ROWS = "/api/v1/statistics/aggregates/rows";

    /** Описание объявленной поверхности: им читается состав маршрутов. */
    protected static final String SURFACE_DESCRIPTION = "/v3/api-docs";

    /** Проба живости — первая открытая точка актуатора. */
    protected static final String LIVENESS_PROBE = "/actuator/health";

    /** Съём рядов — вторая открытая точка актуатора. */
    protected static final String METRICS_SCRAPE = "/actuator/prometheus";

    /**
     * Корень актуатора: он отдаёт ссылки на ПЕРЕЧЕНЬ экспозиции.
     *
     * <p>Им читается сам перечень, а не отдельные его имена: перебор точек
     * доказывает отсутствие названных, а равенство перечня двум объявленным
     * именам — только выдача корня.
     */
    protected static final String ACTUATOR_ROOT = "/actuator";

    /**
     * Ряд возраста последнего принятого события пары.
     *
     * <p><b>Имена рядов написаны формой ЭКСПОЗИЦИИ, а не формой реестра</b>
     * ({@code Constants.ReceptionMetrics}): наблюдатель окружения читает
     * выдачу, и точки имени в ней уже заменены подчёркиваниями. Литерал здесь
     * и есть то, что увидит правило алерта.
     */
    protected static final String AGE_ROW = "statistics_reception_last_event_age_ms";

    /** Ряд порога алерта на лаг пары — доли срока хранения её темы. */
    protected static final String THRESHOLD_ROW = "statistics_reception_lag_alert_threshold_ms";

    /** Ряд остатка непринятого по паре. */
    protected static final String UNCONSUMED_ROW = "statistics_reception_unconsumed_records";

    /**
     * Тенант, которым ходит большинство кейсов.
     *
     * <p><b>Идентичность выбрана так, чтобы не быть ПОДСТРОКОЙ чужих
     * значений, и это не вкус.</b> Клетки прогона утверждают, что названного
     * значения нет НИ В ОДНОЙ колонке строки; короткая форма {@code T1}
     * встречается внутри всякого момента шкалы ISO — {@code 2026-09-20T19:46:38Z},
     * — и такие клетки краснели бы по часу суток, а не по предмету.
     */
    protected static final String TENANT = "TENANT-1";

    /** Второй тенант: им наблюдается радиус чтения. Форма — по тому же доводу. */
    protected static final String SECOND_TENANT = "TENANT-2";

    /** Третий тенант: им наблюдается, что содержимое тенанта не назначает. */
    protected static final String THIRD_TENANT = "TENANT-3";

    /** Заголовок контекста тенанта: тот же, что читает поверхность. */
    protected static final String TENANT_HEADER = "X-Tenant-Id";

    /** Таблица сделочных фактов: поверхности у неё нет вовсе. */
    protected static final String DEAL_FACTS = "deal_facts";

    /** Таблица фактов происшествий: поверхности у неё нет тоже. */
    protected static final String INCIDENT_FACTS = "incident_facts";

    /** Таблица сделочных агрегатов: её строки читает поверхность. */
    protected static final String DEAL_AGGREGATES = "deal_aggregates";

    /** Таблица агрегатов происшествий. */
    protected static final String INCIDENT_AGGREGATES = "incident_aggregates";

    /** Таблица состояния приёма: поверхности у неё нет вовсе. */
    protected static final String RECEPTION_TABLE = "reception_states";

    /**
     * Таблица следа отвергнутого контуром вызова.
     *
     * <p>Лежит в той же базе, что факты и агрегаты, а глубины хранения у неё
     * нет: числа статистики её не касаются вовсе — строку заводит контур, а
     * не приём (docs/models/domain/other/AccessDenial.md §«Природа факта —
     * происшествие»).
     */
    protected static final String DENIALS_TABLE = "access_denials";

    /** Колонка флага остановки приёма пары. */
    protected static final String HALTED_COLUMN = "reception_halted";

    /** Колонка момента последнего принятого события пары. */
    protected static final String LAST_ACCEPTED_COLUMN = "last_accepted_occurred_at";

    /** Колонка момента разрыва в смещениях пары. */
    protected static final String GAP_COLUMN = "lag_gap_at";

    /** Колонка темы — второй половины ключа пары. */
    protected static final String TOPIC_COLUMN = "topic";

    /** Колонка момента обновления строки состояния: её ведёт только тик. */
    protected static final String UPDATED_COLUMN = "updated_at";

    /** Колонка момента, с которого группа наблюдает тему непрерывно. */
    protected static final String OBSERVED_COLUMN = "observed_since";

    /** Колонка признака «тема сейчас в подписке группы». */
    protected static final String SUBSCRIBED_COLUMN = "subscribed";

    /** Колонка имени группы — первой половины ключа пары. */
    protected static final String GROUP_COLUMN = "consumer_group";

    /** Ось времени сделочного факта: момент терминала сделки. */
    protected static final String CLOSED_COLUMN = "closed_at";

    /** Ось времени факта происшествия: момент происшествия из конверта. */
    protected static final String OCCURRED_COLUMN = "occurred_at";

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

    /** Терминал сделки: единственный класс сделочного зерна. */
    protected static final String DEAL_CLOSED = "DEAL_CLOSED";

    /** Заведение сделки: класс зерна происшествий. */
    protected static final String DEAL_OPENED = "DEAL_OPENED";

    /** Решение о создании обычной заявки: класс зерна происшествий. */
    protected static final String ORDER_DECIDED = "ORDER_DECIDED";

    /** Подъём ступени защиты: класс зерна происшествий. */
    protected static final String HOLD_RAISED = "HOLD_RAISED";

    /** Отчёт о происшествии: класс зерна происшествий. */
    protected static final String ANOMALY_REPORTED = "ANOMALY_REPORTED";

    /**
     * Класс события, который производитель объявляет, а статистика не несёт:
     * операнда ни одного зерна его содержимое не несёт
     * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»).
     */
    protected static final String NOT_CARRIED = "DEAL_SHUTDOWN_INITIATED";

    /** Версия формы содержимого штатного прогона. */
    protected static final String FORM_VERSION = "1";

    /** Сделочное зерно: операнд вопроса чтения. */
    protected static final String DEAL_GRAIN = "DEAL";

    /** Зерно происшествий: второе значение того же операнда. */
    protected static final String INCIDENT_GRAIN = "INCIDENT";

    /** Поле суток зерна в строке выдачи. */
    protected static final String BUCKET_DATE = "bucketDate";

    /** Поле момента сборки строки: им различается «пересчитано» и «не тронуто». */
    protected static final String ASSEMBLED_AT = "assembledAt";

    /** Поле счётчика закрытых сделок: им читается объём собранного. */
    protected static final String CLOSED_DEALS = "closedDeals";

    /**
     * Образец текста запроса группировки СДЕЛОЧНОГО зерна.
     *
     * <p><b>Берёт псевдоним ВНЕШНЕЙ выборки, а не имя таблицы.</b> По той же
     * таблице идёт чтение начала ряда, и по имени таблицы счёт сошёлся бы
     * вдвое больший — по причине, которой клетка не ставила. Псевдоним
     * {@code grain} стои́т в первой строке запроса группировки и больше
     * нигде.
     *
     * <p><b>Живёт он здесь, а не у класса клетки:</b> образец читают и та
     * клетка, что считает запросы прохода, и та, что утверждает их
     * ОТСУТСТВИЕ при снятом выключателе, — а вторая запись величины
     * разошлась бы с первой при первой же правке запроса
     * (.claude/rules/carrier-levels.md).
     */
    protected static final String DEAL_GRAIN_QUERY = "%select grain.tenant_id%";

    /**
     * Образец текста запроса группировки зерна ПРОИСШЕСТВИЙ.
     *
     * <p><b>Имя таблицы здесь различает само:</b> к таблице происшествий
     * ходят два запроса — начало ряда и группировка, — и второй добавляет
     * {@code group by}, которого у первого нет. Псевдонимом он не берётся:
     * внешней выборки у него нет вовсе.
     */
    protected static final String INCIDENT_GRAIN_QUERY = "%from incident_facts fact%group by%";

    /**
     * Потолок ожидания асинхронного следа приёма.
     *
     * <p>Ограничен, потому что бесконечное ожидание висело бы ровно там, где
     * приём не идёт; «не упало» проверкой не является.
     */
    protected static final Duration RECEPTION_WAIT = Duration.ofSeconds(60);

    /** Шаг опроса у всякого ожидания ящика. */
    protected static final Duration POLL = Duration.ofMillis(200);

    /**
     * Шаблон числового значения поля в теле ответа.
     *
     * <p><b>Число читается ЛИТЕРАЛОМ тела, а не разобранной картой, и это не
     * придирка.</b> Разбор в карту отдаёт числа двоичным типом с плавающей
     * точкой, и клетка о десятичной записи мерила бы тогда СВОЙ разбор:
     * {@code 0.1 + 0.2} у такого типа не равно {@code 0.3}, а на проводе
     * сумма едет десятичной записью со своим масштабом.
     */
    private static final String NUMBER_FIELD =
            "\"%s\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /**
     * Имя группы потребителя ЭТОГО контекста.
     *
     * <p><b>Читается из свойства, а не из константы субстрата:</b> у класса со
     * своим положением осей группа своя
     * ({@link StatisticsSubstrate#registerOwn}), и зашитая константа читала бы
     * смещения чужой группы — то есть наблюдала бы не тот предмет.
     */
    @Value("${" + StatisticsSubstrate.CONSUMER_GROUP_KEY + "}")
    private String consumerGroup;

    /** Объявленная подписка этого контекста: из неё выводится число пар. */
    @Value("${" + StatisticsSubstrate.TOPICS_KEY + "}")
    private String subscription;

    /**
     * Тик состояния приёма — первая из двух джоб, такт которых ящик подаёт
     * сам. Довод и его дом — в шапке класса.
     */
    @Autowired
    private ReceptionStateJob receptionStateJob;

    /**
     * Тик пересчёта агрегатов — вторая такая джоба.
     *
     * <p><b>Её такт ВХОД, а не подмена, и без него предмета не наблюдать
     * вовсе:</b> строка агрегата есть проекция фактов, собираемая проходом, и
     * событием она не двигается. Расписание у неё выражением CRON, до
     * которого прогон не доживает.
     */
    @Autowired
    private AggregateRecomputeJob aggregateRecomputeJob;

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
     * Подаёт такт тика приёма — до тех пор, пока строки всех пар подписки не
     * заведены.
     *
     * <p><b>Повторяется он не «на всякий случай», а по свойству предмета.</b>
     * Тик молчит, пока приём не жив, а живость он мерит НАЗНАЧЕННЫМИ
     * партициями: сразу после подъёма контекста назначение ещё не состоялось,
     * и первый такт законно не пишет ничего. Один такт, поданный в этот
     * момент, делал бы предусловие клетки гонкой с ребалансировкой группы.
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

    /** Один такт тика приёма — им клетка двигает величины, чей писатель он. */
    protected void tick() {
        receptionStateJob.tick();
    }

    /**
     * Один проход пересчёта агрегатов.
     *
     * <p>Окно и порции решает конфигурация, а сутки зерна — момент события:
     * клетка сдвигает не их, а МОМЕНТ, который кладёт в конверт.
     */
    protected void recompute() {
        aggregateRecomputeJob.tick();
    }

    /**
     * Ставит величины приёма названной пары прямой записью.
     *
     * <p><b>Это durable-ВХОД клетки, а не подмена её выхода.</b> Тропой ящика
     * их не производится: момент последнего принятого двигается только вперёд,
     * и поставить его ПОЗАДИ подаваемого события нечем, а флаг остановки
     * ставит отказ обработки, занимающий единственный поток слушателя до конца
     * прогона. Форма строки при этом объявлена домом и читается наружу
     * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
     * таблица `reception_states`»), поэтому запись по колонкам говорит о том
     * же, о чём читает ассерт.
     *
     * @param topic        тема пары
     * @param lastAccepted момент последнего принятого события
     * @param halted       приём по паре остановлен
     */
    protected void givenReceptionOf(String topic, OffsetDateTime lastAccepted, Boolean halted) {
        rows.write("update " + RECEPTION_TABLE
                        + " set " + LAST_ACCEPTED_COLUMN + " = ?, " + HALTED_COLUMN + " = ?"
                        + " where " + GROUP_COLUMN + " = ? and " + TOPIC_COLUMN + " = ?",
                lastAccepted, halted, consumerGroup(), topic);
    }

    /**
     * Ставит момент обнаруженного разрыва названной паре прямой записью.
     *
     * <p><b>Это durable-ВХОД клетки по тому же доводу, что и величины
     * выше.</b> Тропой ящика разрыв производится только на назначении
     * партиций либо на доставке с пропуском — оба хода двигают заодно и
     * смещения, о неподвижности которых клетка утверждает.
     *
     * @param topic тема пары
     * @param gapAt момент обнаруженного разрыва
     */
    protected void givenGapOf(String topic, OffsetDateTime gapAt) {
        rows.write("update " + RECEPTION_TABLE + " set " + GAP_COLUMN + " = ?"
                        + " where " + GROUP_COLUMN + " = ? and " + TOPIC_COLUMN + " = ?",
                gapAt, consumerGroup(), topic);
    }

    /** Имя группы потребителя этого контекста. */
    protected String consumerGroup() {
        return consumerGroup;
    }

    /** Темы объявленной подписки этого контекста в порядке записи. */
    protected List<String> subscription() {
        return List.of(subscription.split(","));
    }

    /** Единственная тема подписки этого контекста. */
    protected String topic() {
        return subscription().getFirst();
    }

    /**
     * Кладёт в тему запись с ПОЛНЫМ конвертом.
     *
     * @param eventId    идентичность события
     * @param eventType  класс события
     * @param occurredAt момент происшествия
     * @param content    содержимое дословно
     */
    protected void publish(String eventId, String eventType, OffsetDateTime occurredAt, String content) {
        publish(topic(), eventId, eventType, occurredAt, content);
    }

    /**
     * Кладёт запись с полным конвертом в НАЗВАННУЮ тему подписки.
     *
     * <p><b>Тема стои́т параметром там, где подписка объявлена двумя.</b>
     * Клетка о радиусе разрыва утверждает о РАЗНЫХ парах — «у своей момент
     * записан, у соседней пуст», — и на теме по умолчанию такое утверждение не
     * выразимо вовсе.
     *
     * @param topic      тема подписки
     * @param eventId    идентичность события
     * @param eventType  класс события
     * @param occurredAt момент происшествия
     * @param content    содержимое дословно
     */
    protected void publish(String topic, String eventId, String eventType,
                           OffsetDateTime occurredAt, String content) {
        Wire.publish(topic, TENANT, envelope(eventId, eventType, occurredAt), content);
    }

    /**
     * Полный конверт названного события: пять заголовков, которые ставит
     * построенный публикатор.
     *
     * @param eventId    идентичность события
     * @param eventType  класс события
     * @param occurredAt момент происшествия
     */
    protected static Map<String, String> envelope(String eventId, String eventType,
                                                  OffsetDateTime occurredAt) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EVENT_ID, eventId);
        headers.put(EVENT_TYPE, eventType);
        headers.put(OCCURRED_AT, occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        headers.put(VERSION, FORM_VERSION);
        headers.put(TRACE_CONTEXT, "00-" + eventId + "-0000000000000001-01");
        return headers;
    }

    /**
     * Ждёт, пока группа зафиксирует смещение конца темы подписки.
     *
     * <p><b>Это и есть наблюдаемый конец обработки.</b> Строка факта говорит
     * только о принятом; равенство смещений говорит, что обработано ВСЁ
     * положенное — включая записи, которых строки не оставляют вовсе (повтор,
     * поглощённый ключом; событие ненесомого класса).
     */
    protected void awaitConsumed() {
        awaitConsumed(topic());
    }

    /**
     * Ждёт, пока группа зафиксирует смещение конца НАЗВАННОЙ темы.
     *
     * @param topic тема подписки
     */
    protected void awaitConsumed(String topic) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Wire.endOffset(topic),
                        Wire.committedOffset(consumerGroup, topic)));
    }

    /**
     * Ждёт, пока сделочных фактов окажется названное число.
     *
     * <p><b>Нужен там, где конец обработки смещением не наблюдается.</b>
     * Управляющая запись транзакции занимает своё смещение и потребителю не
     * отдаётся ({@link Wire#publishInTransaction}), поэтому равенство «конец
     * темы = зафиксированное смещение» на такой теме не наступает вовсе, и
     * {@link #awaitConsumed} истекал бы по таймауту при исправной системе.
     *
     * @param expected сколько строк сделочного зерна обязано лежать
     */
    protected void awaitDealFactCount(Long expected) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(DEAL_FACTS), expected));
    }

    /**
     * Ждёт флага остановки приёма по паре подписки.
     *
     * <p><b>Ожидается ФЛАГ, а не смещение.</b> На тропе отказа обработки
     * смещение не двигается вовсе, и ждать его продвижения значило бы ждать
     * таймаута; флаг же ставит обработчик отказа на ПЕРВОЙ неудачной доставке
     * ({@code ReceptionHaltMarker}) — он и есть наблюдаемый конец первой
     * попытки.
     */
    protected void awaitHalted() {
        awaitHalted(topic());
    }

    /**
     * Ждёт флага остановки приёма по НАЗВАННОЙ паре.
     *
     * @param topic тема пары
     */
    protected void awaitHalted(String topic) {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Boolean.TRUE, pair(topic).get(HALTED_COLUMN)));
    }

    /**
     * Подаёт НАЗНАЧЕНИЕ ПАРТИЦИЙ — вход первого момента обнаружения разрыва
     * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва —
     * сравнение смещений, и моментов у него два»).
     *
     * <p>Довод формы хода и его цена живут у самого хода
     * ({@link Wire#reassignPartitions}); здесь — только подписка ЭТОГО
     * контекста, которую он перетряхивает.
     */
    protected void reassignPartitions() {
        Wire.reassignPartitions(consumerGroup, subscription());
    }

    /**
     * Строки сделочных фактов, упорядоченные идентичностью события.
     *
     * <p><b>Порядок задаёт ИДЕНТИЧНОСТЬ, а не суррогатный ключ:</b> у
     * гипертаблицы его нет вовсе (его запрещает Timescale), и общего «порядка
     * записи» у строк не существует. Клетка, читающая ряд, упорядочивает его
     * тем, что сама и подала.
     */
    protected List<Map<String, Object>> dealFacts() {
        return rows.all(DEAL_FACTS, "event_id");
    }

    /** Единственный сделочный факт; иное число — падение. */
    protected Map<String, Object> dealFact() {
        return single(dealFacts(), DEAL_FACTS);
    }

    /** Строки фактов происшествий, упорядоченные идентичностью события. */
    protected List<Map<String, Object>> incidentFacts() {
        return rows.all(INCIDENT_FACTS, "event_id");
    }

    /** Все строки состояния приёма, упорядоченные темой. */
    protected List<Map<String, Object>> pairs() {
        return rows.all(RECEPTION_TABLE, TOPIC_COLUMN);
    }

    /** Строка состояния приёма по названной теме. */
    protected Map<String, Object> pair(String topic) {
        return rows.row(RECEPTION_TABLE, TOPIC_COLUMN, topic);
    }

    /** Версия строки состояния названной пары: ею видно повторную запись. */
    protected String pairVersion(String topic) {
        return rows.rowVersion(RECEPTION_TABLE, TOPIC_COLUMN, topic);
    }

    /**
     * Предикат непрерывности, едущий с выдачей чтения.
     *
     * <p><b>Читается он ЧЕРЕЗ ПОВЕРХНОСТЬ</b>, а не считается тестом по
     * колонкам: предмет величины в том, что статистика объявляет о себе
     * читателю рядом с числами, а не отдельным запросом
     * (docs/rules/durable-consumer-reception.md §«Величины едут рядом с
     * числами, а не отдельным запросом»).
     */
    protected Object continuityClaimable() {
        return completeness().get("continuityClaimable");
    }

    /** Нижняя граница полноты, едущая с той же выдачей. */
    protected Object lowerBound() {
        return completeness().get("lowerBound");
    }

    /**
     * Нижняя граница полноты точкой шкалы: ею границы СРАВНИВАЮТСЯ.
     *
     * <p>Отсутствие границы — падение, а не пустая точка: клетки, читающие её
     * сравнением, стоя́т на заведённых строках пар, и пустота означала бы, что
     * предусловие не поставлено.
     */
    protected OffsetDateTime lowerBoundMoment() {
        Object value = lowerBound();
        if (Objects.isNull(value)) {
            throw new AssertionError("Нижняя граница полноты отсутствует: обещать нечего");
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private Map<String, Object> completeness() {
        return aggregates(DEAL_GRAIN, TENANT).completeness();
    }

    /**
     * Страница агрегатов названного зерна за окно пересчёта.
     *
     * <p><b>Агрегаты читаются ПОВЕРХНОСТЬЮ, а не колонками</b>: признак
     * решения 7 — объявленный читатель вне кода владельца — у них выполнен, и
     * читает он их именно так (.claude/tests/cases/statistics.md §«Чем
     * достаются выходы»).
     *
     * <p>Окно берётся шириной окна пересчёта: у́же оно не накрыло бы суток,
     * которые проход собрал, а шире — было бы отвергнуто пределом ширины.
     *
     * @param grain            зерно строки
     * @param tenantInternalId тенант заголовка контекста
     */
    protected Answer aggregates(String grain, String tenantInternalId) {
        return aggregatesSince(grain, tenantInternalId,
                StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS - 1);
    }

    /**
     * Страница агрегатов названного зерна за окно НАЗВАННОЙ ширины.
     *
     * <p><b>Ширина стои́т параметром у клеток ОКНА, и это не удобство.</b>
     * Окно чтения и окно пересчёта — разные величины разных владельцев
     * ({@code AggregateReadProperties}): первое ограничивает запрошенное
     * читателем, второе назначает объём работы прохода. Клетка, сдвинувшая
     * окно пересчёта, обязана читать шире него — иначе сутки, которых проход
     * не собрал, и сутки, которых не спросило чтение, стали бы неразличимы.
     *
     * @param grain            зерно строки
     * @param tenantInternalId тенант заголовка контекста
     * @param daysBack         сколько суток назад открывается окно чтения;
     *                         правая граница — нынешние сутки
     */
    protected Answer aggregatesSince(String grain, String tenantInternalId, Integer daysBack) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return get(AGGREGATE_ROWS + "?grain=" + grain
                + "&from=" + today.minusDays(daysBack) + "&to=" + today, tenantInternalId);
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

    /**
     * Полночь суток, отстоящих от нынешних на названное число, — UTC.
     *
     * <p><b>Ею выбираются сутки зерна у клеток, чей предмет — строка
     * АГРЕГАТА.</b> Проход пересчёта не пишет суток, начавшихся раньше первого
     * факта ряда (docs/spec/statistics-aggregates.json,
     * {@code dayRecomputable}): факт, положенный в середину суток, оставил бы
     * эти сутки непокрытыми частично, и строки не появилось бы вовсе — то
     * есть клетка краснела бы по охране отбора, а не по своему предмету.
     * Граница включающая, поэтому полночь ровно покрывает сутки целиком.
     *
     * @param daysBack сколько суток назад от нынешних
     */
    protected static OffsetDateTime midnightDaysAgo(Integer daysBack) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysBack)
                .atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    }

    /**
     * Сутки, отстоящие от нынешних на названное число, — ЗАПИСЬЮ выдачи.
     *
     * <p>Сутки зерна едут наружу датой без времени, и клетка сравнивает их с
     * тем, что написала бы сама, а не с разобранным типом.
     *
     * @param daysBack сколько суток назад от нынешних
     */
    protected static String day(Integer daysBack) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysBack).toString();
    }

    /**
     * Те же сутки — ТИПОМ КОЛОНКИ, а не записью выдачи.
     *
     * <p>Ею клетка кладёт строку агрегата прямой записью и ищет её в базе;
     * запись выдачи ({@link #day}) сравнивается с тем, что пришло наружу.
     * Носитель у величины один: вторая её запись разошлась бы с первой при
     * первой же правке (.claude/rules/carrier-levels.md).
     *
     * @param daysBack сколько суток назад от нынешних
     */
    protected static LocalDate bucket(Integer daysBack) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysBack);
    }

    /**
     * РАЗЛИЧНЫЕ моменты названного поля у строк выдачи.
     *
     * <p>Ею читается утверждение «момент у всех строк один»: счёт множества
     * отвечает на него прямо, а перечень по строкам отвечал бы на него
     * порядком выдачи.
     *
     * @param handed строки выдачи
     */
    protected static Set<String> distinctMomentsOf(List<Map<String, Object>> handed) {
        return handed.stream()
                .map(row -> String.valueOf(row.get(ASSEMBLED_AT)))
                .collect(Collectors.toSet());
    }

    /** Сутки зерна у каждой строки выдачи. */
    protected static List<String> bucketDatesOf(List<Map<String, Object>> handed) {
        return handed.stream().map(row -> String.valueOf(row.get(BUCKET_DATE))).toList();
    }

    /**
     * Строка выдачи названных суток; иное их число — падение клетки.
     *
     * @param handed   строки выдачи
     * @param daysBack сколько суток назад от нынешних
     */
    protected static Map<String, Object> rowOf(List<Map<String, Object>> handed, Integer daysBack) {
        List<Map<String, Object>> found = handed.stream()
                .filter(row -> Objects.equals(day(daysBack), String.valueOf(row.get(BUCKET_DATE))))
                .toList();
        if (found.size() != 1) {
            throw new AssertionError(
                    "Ожидалась ровно одна строка суток " + day(daysBack) + ", их " + found.size());
        }
        return found.getFirst();
    }

    /**
     * Момент названного поля строки выдачи.
     *
     * @param row   строка выдачи
     * @param field имя поля с моментом
     */
    protected static OffsetDateTime moment(Map<String, Object> row, String field) {
        return OffsetDateTime.parse(String.valueOf(row.get(field)));
    }

    /** Момент колонки как точка шкалы: смещение записи контракта не несёт. */
    protected static Instant instant(Map<String, Object> row, String column) {
        return ((OffsetDateTime) row.get(column)).toInstant();
    }

    /** Значение денежной колонки как десятичное число. */
    protected static BigDecimal decimal(Map<String, Object> row, String column) {
        return (BigDecimal) row.get(column);
    }

    /**
     * Значение ряда возраста последнего принятого события по теме; пусто —
     * ряда нет.
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
     * <p>Снимается ЭКСПОЗИЦИЕЙ, а не реестром: наблюдатель окружения читает
     * именно её, и ряд, живущий только в реестре, до правила алерта не
     * доезжает.
     *
     * <p><b>Пустота здесь означает «ряда нет», а не «ноль»</b>, и это несущее
     * различение: неизмеренная величина ряда не получает вовсе, а поданная
     * числом молча решила бы исход правила
     * (docs/components/ReceptionStateJob.md §«Ряды экспорта пишет тот же
     * тик»).
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
     * Сколько рядов приёма несёт выдача — по всем трём величинам и всем парам
     * разом.
     *
     * <p>Им выражается отрицание «рядов не осталось ни одного»: такт, который
     * не измерил, уносит их ВСЕ, а не обнуляет.
     */
    protected Long receptionRowCount() {
        return scrape().lines()
                .filter(line -> line.startsWith(AGE_ROW)
                        || line.startsWith(THRESHOLD_ROW)
                        || line.startsWith(UNCONSUMED_ROW))
                .count();
    }

    /**
     * Маршруты, которые сервис отображает наружу.
     *
     * <p><b>Описание поверхности сильнее перебора имён:</b> оно отвечает на
     * «сколько маршрутов есть», тогда как перебор — только на «нет ли вот
     * этих». Отрицания о несуществующих точках поэтому стоя́т на нём.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> surfaceRoutes() {
        return (Map<String, Object>) get(SURFACE_DESCRIPTION, TENANT).asObject().get("paths");
    }

    /** Выдача экспозиции дословно. */
    protected String scrape() {
        return get(METRICS_SCRAPE, TENANT).body();
    }

    /**
     * Обращение произвольным методом: им перебирается поверхность.
     *
     * <p><b>Отрицание «точки нет» доказывается перебором МЕТОДОВ, а не одним
     * запросом:</b> отсутствующий путь и неподдержанный метод отвечают
     * разными кодами, и клетка, спросившая один метод, не отличила бы «точки
     * нет» от «точка есть, но берёт другой глагол».
     *
     * @param method глагол обращения
     * @param path   путь поверхности
     */
    protected Answer call(String method, String path) {
        return send(request(path)
                .header("Authorization", "Bearer " + identity.serviceToken())
                .header(TENANT_HEADER, TENANT)
                .method(method, HttpRequest.BodyPublishers.noBody()));
    }

    /**
     * Путь агрегатной выборки с названными операндами запроса.
     *
     * <p><b>Операнды даются ПАРАМИ, а не отдельными аргументами зерна и
     * границ, и это не удобство:</b> половина клеток группы {@code B10}
     * спрашивает поверхность ровно тем, чего у вопроса НЕ ХВАТАЕТ — зерна,
     * одной границы окна, половины позиции, — и подпись с обязательными
     * аргументами выразить такой вопрос не даёт вовсе.
     *
     * <p>Носитель у сборки один на оба класса группы: вторая её запись
     * разошлась бы с первой при первом же переименовании операнда
     * (.claude/rules/carrier-levels.md).
     *
     * @param operands пары «имя операнда, значение»; нечётное число —
     *                 падение клетки на месте её письма
     */
    protected static String aggregatePath(String... operands) {
        if (operands.length % 2 != 0) {
            throw new AssertionError("Операнды запроса даются парами: их " + operands.length);
        }
        StringBuilder path = new StringBuilder(AGGREGATE_ROWS);
        for (int index = 0; index < operands.length; index += 2) {
            path.append(index == 0 ? '?' : '&')
                    .append(operands[index])
                    .append('=')
                    .append(URLEncoder.encode(operands[index + 1], StandardCharsets.UTF_8));
        }
        return path.toString();
    }

    /**
     * Чтение под сервисным токеном, БЕЗ заголовка контекста тенанта.
     *
     * <p><b>Им наблюдается отказ, который производит КОНТЕЙНЕР, а не наш
     * код:</b> обязательность заголовка выражена контрактом точки, и класс
     * такого отказа другой — форма вызова отвергается раньше, чем вопрос
     * чтения доходит до выборки
     * (.claude/tests/cases/statistics.md §«Число ответа и класс отказа —
     * разные ожидания»).
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
     * утверждает, что вызов отвергает КОНТУР, а не разбор контекста тенанта,
     * — а непредъявленный заголовок контекста отвергает контейнер своим
     * классом (.claude/tests/cases/statistics.md §«Число ответа и класс
     * отказа — разные ожидания»). Без заголовка два отказа стали бы
     * неразличимы по поводу, совпав по коду.
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

    /** Чтение под сервисным токеном и контекстом названного тенанта. */
    protected Answer get(String path, String tenantInternalId) {
        return send(request(path)
                .header("Authorization", "Bearer " + identity.serviceToken())
                .header(TENANT_HEADER, tenantInternalId)
                .GET());
    }

    private static Map<String, Object> single(List<Map<String, Object>> found, String table) {
        if (found.size() != 1) {
            throw new AssertionError(
                    "Ожидалась ровно одна строка таблицы " + table + ", их " + found.size());
        }
        return found.getFirst();
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
     * <p><b>Запись, а не разобранный объект:</b> часть клеток утверждает о
     * ЧИСЛЕ в теле — что денежная сумма едет десятичной записью, — и разбор в
     * типизованную форму такое ожидание выразить не даёт.
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
         * <p>Имя ищется БЕЗ учёта регистра: регистр имени заголовка контракта
         * не несёт, и ассерт, чувствительный к нему, мерил бы написание, а не
         * наличие.
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
         * Несёт ли тело единый error-DTO поверхности: класс отказа и момент.
         *
         * <p>Форма читается по ПОЛЯМ, а не по коду ответа: отказ контейнера
         * отдаёт то же тело при своём статусе, а умолчание ресурс-сервера
         * отвечает ПУСТЫМ телом — то есть вторым форматом, существование
         * которого клейм «единый DTO» и отрицает.
         *
         * <p><b>Неразобранное тело даёт ОТВЕТ, а не отказ клетки:</b> иначе
         * красная клетка падала бы разбором и переставала называть, чем
         * ожидание не сошлось.
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

        /** Строки сделочного зерна; пусто — спрошено другое зерно. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dealRows() {
            return (List<Map<String, Object>>) asObject().get("dealRows");
        }

        /** Строки зерна происшествий; пусто — спрошено другое зерно. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> incidentRows() {
            return (List<Map<String, Object>>) asObject().get("incidentRows");
        }

        /** Объявленная полнота, едущая с каждой выдачей чисел. */
        @SuppressWarnings("unchecked")
        Map<String, Object> completeness() {
            return (Map<String, Object>) asObject().get("completeness");
        }

        /**
         * Позиция продолжения; пусто — окно дочитано.
         *
         * <p><b>Пустота здесь означает «продолжения нет», и второго смысла у
         * неё не бывает:</b> позиция, названная наполовину, вопросом не
         * принимается вовсе (docs/rules/absent-value-semantics.md).
         */
        @SuppressWarnings("unchecked")
        Map<String, Object> nextCursor() {
            return (Map<String, Object>) asObject().get("nextCursor");
        }

        /**
         * Числовое значение названного поля ЛИТЕРАЛОМ тела.
         *
         * <p>Довод формы — у шаблона ({@link StatisticsBox#NUMBER_FIELD}):
         * разобранная карта отдала бы двоичный тип, и клетка о десятичной
         * записи мерила бы свой разбор.
         *
         * @param field имя поля
         */
        BigDecimal number(String field) {
            Matcher matcher = Pattern.compile(NUMBER_FIELD.formatted(field)).matcher(body);
            if (isFalse(matcher.find())) {
                throw new AssertionError("В теле ответа нет числового поля " + field + ": " + body);
            }
            return new BigDecimal(matcher.group(1));
        }
    }
}
