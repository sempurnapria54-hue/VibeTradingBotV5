package com.example.statistics.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B11} документа кейсов: контур доступа и форма отказа
 * (.claude/tests/cases/statistics.md §«B11 — Контур доступа и форма отказа»).
 *
 * <p><b>Вход этой группы подаётся ТЕМ, ЧЕГО У ВЫЗОВА НЕТ.</b> У соседних
 * групп вход — запись брокера либо вопрос читателя; здесь входом служит
 * непредъявленная либо неудостоверенная идентичность.
 *
 * <p><b>Нового наблюдателя выхода группа не заводит, а заводит новую ТАБЛИЦУ
 * у прежнего:</b> след отвергнутого вызова читается тем же наблюдателем строк
 * ({@link Rows}), которым читаются факты, агрегаты и строки состояния, и до
 * этой группы его не читала ни одна клетка.
 *
 * <p><b>Числами пиньнуты ровно те исходы, чьё число фиксирует дом:</b>
 * {@code 401} точки входа контура, {@code 404} и {@code 405} контейнера,
 * {@code 200} — контракт успеха открытых точек
 * (.claude/tests/cases/statistics.md §«Число ответа и класс отказа — разные
 * ожидания»).
 *
 * <p><b>Форма тела читается по ПОЛЯМ, а не по коду ответа.</b> Клейм «единый
 * error-DTO» держится ровно тогда, когда своё тело получают и те отказы,
 * которых обработчик не называет поимённо: отказ контура и отказ контейнера.
 * Умолчание ресурс-сервера отвечает ПУСТЫМ телом — то есть вторым форматом,
 * существование которого клейм и отрицает.
 *
 * <p><b>Клетки {@code B11.8} здесь нет, и это не пропуск.</b>
 * Пер-операционных проверок права у контура нет ни одной, и отказ по правам
 * изнутри обработчика недостижим ни одной тропой ящика
 * (.claude/tests/cases/statistics.md §«Кейсы, не прогоняемые сегодня»).
 * Ожидание при этом <b>мерится</b> пробой предмета
 * ({@code AggregateSurfaceReadTest}), заведённой этим же заходом: без неё
 * клейм «перехватчик непредусмотренного отказа по правам не съедает» не
 * держался бы ни на чём, а регресс ждал бы первой проверки права, то есть
 * чужого шага.
 *
 * <p><b>Отрицание «чисел статистики в выдаче съёма нет» взято ПОЛОЖИТЕЛЬНОЙ
 * формой</b> — см. {@link #theMetricsScrapeIsOpenAndThatIsTheSecondException}.
 */
class AccessContourBoxTest extends SharedStatisticsBox {

    /** Класс отказа контура: принятого принципала нет. */
    private static final String UNAUTHENTICATED = "ACCESS_UNAUTHENTICATED";

    /** Класс отказа, произведённого контейнером. */
    private static final String NOT_ACCEPTED = "REQUEST_NOT_ACCEPTED";

    /** Значение класса отказа в строке следа. */
    private static final String PRINCIPAL_ABSENT = "PRINCIPAL_ABSENT";

    /** Колонка поверхности, к которой обратились: метод и путь. */
    private static final String SURFACE_COLUMN = "surface";

    /** Колонка класса отказа. */
    private static final String OUTCOME_COLUMN = "outcome";

    /** Колонка принятого принципала: пусто ⟺ принципала нет. */
    private static final String PRINCIPAL_COLUMN = "principal";

    /** Колонка внешней идентичности строки. */
    private static final String INTERNAL_ID_COLUMN = "internal_id";

    /** Колонка момента создания строки: её проставляет персистентность. */
    private static final String CREATED_COLUMN = "created_at";

    /** Суррогатный ключ: им строки следа читаются в порядке заведения. */
    private static final String SURROGATE_COLUMN = "id";

    /**
     * Ширина колонки поверхности.
     *
     * <p><b>Объявлена здесь величиной по тому же доводу, что оси
     * субстрата:</b> клетка о длинном пути утверждает о самой ГРАНИЦЕ
     * усечения, и знать её обязан кейс, а не код сервиса.
     */
    private static final Integer SURFACE_WIDTH = 256;

    /** Запись журнала о том, что след завести не удалось. */
    private static final String WRITE_FAILURE = "Access denial row not persisted";

    /** Схема предъявления, которую контур называет отвергнутому. */
    private static final String CHALLENGE_HEADER = "WWW-Authenticate";

    /**
     * Точки актуатора ВНЕ перечня экспозиции.
     *
     * <p>Перебором доказывается отсутствие названных, а не равенство перечня:
     * последнее читается выдачей корня актуатора ({@link #exposedEndpoints()}).
     * Точек живости в перечне нет намеренно — {@code /actuator/health/**}
     * открыт целиком.
     */
    private static final List<String> CLOSED_ACTUATOR_POINTS = List.of(
            "/actuator/env",
            "/actuator/configprops",
            "/actuator/mappings",
            "/actuator/threaddump",
            "/actuator/beans",
            "/actuator/loggers",
            "/actuator/metrics",
            "/actuator/heapdump",
            "/actuator/info",
            "/actuator/conditions",
            "/actuator/scheduledtasks",
            "/actuator/flyway",
            "/actuator/caches",
            "/actuator/startup",
            "/actuator/httpexchanges",
            "/actuator/shutdown");

    /** Поле состояния процесса в ответе пробы живости. */
    private static final String HEALTH_STATUS = "status";

    /**
     * Поле имён групп проб.
     *
     * <p><b>Данными предмета оно не является, и потому в перечне стои́т наравне
     * с состоянием:</b> имена групп — собственный контракт актуатора, а не то,
     * что знает сервис. Подробностей о базе и брокере рядом с ними нет — их
     * гасит умолчание {@code show-details}, и равенство перечня двум полям это
     * и предъявляет.
     */
    private static final String HEALTH_GROUPS = "groups";

    /** Имена открытых точек: ими сверяется перечень экспозиции. */
    private static final List<String> EXPOSED_ENDPOINTS = List.of("health", "prometheus");

    /** Хвост имени ссылки, которым актуатор помечает путевой вариант точки. */
    private static final String PATH_LINK_SUFFIX = "-path";

    /**
     * Префикс, которым сервис называет СВОИ ряды.
     *
     * <p>Имена собственных рядов строятся одной конвенцией
     * ({@code Constants.ReceptionMetrics}): имя сервиса первым сегментом,
     * дальше предмет. Экспозиция заменяет точки подчёркиваниями, и этот
     * префикс есть то, чем выдача отличает наши ряды от рядов каркаса.
     */
    private static final String OWN_SERIES_PREFIX = "statistics_";

    /** Сутки, в которых лежат факты клетки о съёме рядов. */
    private static final Integer DAY = 1;

    /** Сколько одинаковых попыток подаёт клетка о счётности следа. */
    private static final Integer ATTEMPTS = 3;

    /** Длина пути, заведомо превышающая ширину колонки поверхности. */
    private static final Integer OVERSIZED_PATH_LENGTH = 400;

    /** Ширина окна, которым клетки этой группы спрашивают агрегаты. */
    private static final Integer WINDOW = StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS - 1;

    @Test
    @DisplayName("B11.1 — Умолчание закрыто: вызов без принципала отвергается и оставляет след")
    void theDefaultIsClosedAndACallWithoutAPrincipalLeavesATrace() {
        Answer answer = getAnonymously(question());

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto())
                .as("тело — тот же error-DTO, что у всякой ошибки поверхности, а не пустое")
                .isTrue();
        assertThat(answer.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(answer.header(CHALLENGE_HEADER))
                .as("схема предъявления названа: контур принимает bearer-токен")
                .isEqualTo("Bearer");
        assertThat(answer.header("Set-Cookie"))
                .as("сессия не заводится: поверхность stateless")
                .isNull();
        assertThat(answer.dealRows())
                .as("строк агрегатов в теле отказа нет")
                .isNull();

        Map<String, Object> denial = onlyDenial();
        assertThat(denial.get(OUTCOME_COLUMN)).isEqualTo(PRINCIPAL_ABSENT);
        assertThat(denial.get(PRINCIPAL_COLUMN))
                .as("заявленное, но не удостоверенное имя в строку не пишется")
                .isNull();
        assertThat(denial.get(SURFACE_COLUMN))
                .as("поверхность несёт метод и путь, а операнды запроса — нет: их "
                        + "содержимое под контролем вызывающего")
                .isEqualTo("GET " + AGGREGATE_ROWS);
        assertThat(denial.get(INTERNAL_ID_COLUMN)).isNotNull();
        assertThat(denial.get(CREATED_COLUMN))
                .as("момент создания проставляет персистентность")
                .isNotNull();
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("след отказа и числа статистики — разные предметы")
                .isZero();
    }

    @Test
    @DisplayName("B11.2 — Токен, подписанный чужим ключом, не принимается")
    void aTokenSignedWithAForeignKeyIsRefused() {
        Answer answer = getWith(question(), identity.foreignKeyToken());

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(identity.paths())
                .as("за подтверждением сервис ходил только к точкам ключей: подпись "
                        + "проверена локально, и вызова к провайдеру на каждый запрос нет")
                .isNotEmpty()
                .allMatch(path -> path.contains("jwks") || path.contains("openid-configuration"));

        Map<String, Object> denial = onlyDenial();
        assertThat(denial.get(OUTCOME_COLUMN))
                .as("неудостоверенное имя неизвестно ровно так же, как непредъявленное")
                .isEqualTo(PRINCIPAL_ABSENT);
        assertThat(denial.get(PRINCIPAL_COLUMN)).isNull();
    }

    @Test
    @DisplayName("B11.3 — Просроченный токен и токен чужого издателя")
    void anExpiredTokenAndATokenOfAForeignIssuerAreBothRefused() {
        Answer expired = getWith(question(), identity.expiredToken());
        Answer foreignIssuer = getWith(question(), identity.foreignIssuerToken());

        assertThat(expired.status()).isEqualTo(401);
        assertThat(foreignIssuer.status())
                .as("издатель проверяется: сервис объявил точку издателя, а не только "
                        + "адрес ключей")
                .isEqualTo(401);
        assertThat(expired.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(foreignIssuer.errorCode()).isEqualTo(UNAUTHENTICATED);

        List<Map<String, Object>> denials = denials();
        assertThat(denials).as("след оставляет каждая из двух попыток").hasSize(2);
        assertThat(denials).allSatisfy(denial -> {
            assertThat(denial.get(OUTCOME_COLUMN)).isEqualTo(PRINCIPAL_ABSENT);
            assertThat(denial.get(PRINCIPAL_COLUMN)).isNull();
        });
    }

    @Test
    @DisplayName("B11.4 — Проба живости открыта")
    void theLivenessProbeIsOpen() {
        Answer answer = getAnonymously(LIVENESS_PROBE);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject())
                .as("тело несёт СОБСТВЕННЫЙ контракт пробы — состояние и имена групп "
                        + "проб, — и ни одного поля предмета: ни подробностей о базе и "
                        + "брокере, ни чисел статистики")
                .containsOnlyKeys(HEALTH_STATUS, HEALTH_GROUPS);
        assertThat(answer.body())
                .as("данных о тенантах ответ не несёт")
                .doesNotContain(TENANT);
        assertThat(rows.count(DENIALS_TABLE))
                .as("открытая точка следа отказа не заводит")
                .isZero();
    }

    /**
     * <b>Отрицание «чисел статистики в выдаче нет» взято ПОЛОЖИТЕЛЬНОЙ
     * формой:</b> всякий ряд, названный префиксом сервиса, есть один из трёх
     * рядов приёма. Перечень запрещённых имён («сумма», «счётчик»,
     * «агрегат») пришлось бы держать полным, а число под непредусмотренным
     * именем осталось бы ему невидимым.
     *
     * <p><b>Вторая форма отрицания независима от первой:</b> число статистики
     * едет по ключу зерна, и ряд, его несущий, назвал бы тенанта, счёт либо
     * определение стратегии меткой. Ни одного из трёх имён в выдаче нет —
     * при том, что факты и собранные из них строки в базе ЛЕЖАТ, и клетка
     * это предъявляет.
     */
    @Test
    @DisplayName("B11.5 — Съём рядов открыт, и это второе исключение")
    void theMetricsScrapeIsOpenAndThatIsTheSecondException() {
        givenReceptionStateRows();
        Facts.deal("E-11-5", TENANT, midnightDaysAgo(DAY));
        recompute();

        Answer answer = getAnonymously(METRICS_SCRAPE);

        assertThat(answer.status())
                .as("точка ОБЪЯВЛЕНА в перечне экспозиции: без объявления реестр работал "
                        + "бы, а наблюдатель получал бы 404")
                .isEqualTo(200);
        for (String topic : subscription()) {
            for (String series : List.of(AGE_ROW, THRESHOLD_ROW, UNCONSUMED_ROW)) {
                assertThat(answer.body())
                        .as("ряд " + series + " по теме " + topic)
                        .contains(series + "{topic=\"" + topic + "\"}");
            }
        }
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("вход поставлен: число, которое могло бы уехать рядом, собрано")
                .isEqualTo(1L);
        assertThat(ownSeriesOf(answer.body()))
                .as("всякий ряд сервиса есть ряд ПРИЁМА: чисел статистики — сумм, "
                        + "счётчиков, агрегатов — в выдаче нет ни одного")
                .containsExactlyInAnyOrder(AGE_ROW, THRESHOLD_ROW, UNCONSUMED_ROW);
        assertThat(answer.body())
                .as("и ключа зерна в выдаче нет ни одним компонентом: число статистики "
                        + "ехало бы по нему меткой")
                .doesNotContain(TENANT)
                .doesNotContain(Facts.ACCOUNT)
                .doesNotContain(Facts.STRATEGY);
        assertThat(rows.count(DENIALS_TABLE))
                .as("вторая открытая точка следа отказа не заводит тоже")
                .isZero();
    }

    @Test
    @DisplayName("B11.6 — Прочие точки актуатора и описание поверхности не открыты")
    void noOtherActuatorPointAndNoSurfaceDescriptionIsOpen() {
        for (String point : CLOSED_ACTUATOR_POINTS) {
            assertThat(getAnonymously(point).status())
                    .as("без принципала точка не отдаёт данных: " + point)
                    .isNotEqualTo(200);
            assertThat(get(point, TENANT).status())
                    .as("точки нет в перечне экспозиции: " + point)
                    .isEqualTo(404);
        }
        assertThat(exposedEndpoints())
                .as("перечень экспозиции равен двум объявленным именам")
                .containsExactlyInAnyOrderElementsOf(EXPOSED_ENDPOINTS);

        Answer description = getAnonymously(SURFACE_DESCRIPTION);
        assertThat(description.status())
                .as("описание поверхности закрыто умолчанием — в исключения оно не входит")
                .isEqualTo(401);
        assertThat(description.body()).doesNotContain(AGGREGATE_ROWS);
    }

    @Test
    @DisplayName("B11.7 — Неизвестный путь и неподдержанный метод отвечают тем же телом")
    void anUnknownPathAndAnUnsupportedMethodCarryTheSameBody() {
        Answer unknownPath = get(AGGREGATE_ROWS + "/whatever", TENANT);
        Answer wrongMethod = call("POST", AGGREGATE_ROWS);

        assertThat(unknownPath.status()).isEqualTo(404);
        assertThat(wrongMethod.status()).isEqualTo(405);
        assertThat(unknownPath.carriesErrorDto())
                .as("статус наследуется от контейнера, подменяется только тело")
                .isTrue();
        assertThat(wrongMethod.carriesErrorDto()).isTrue();
        assertThat(unknownPath.errorCode())
                .as("форму вызова отвергает контейнер, и класс у неё СВОЙ — не тот, "
                        + "которым отвергается вопрос чтения")
                .isEqualTo(NOT_ACCEPTED);
        assertThat(wrongMethod.errorCode()).isEqualTo(NOT_ACCEPTED);
        assertThat(unknownPath.errorMessage()).isNotBlank();
        assertThat(wrongMethod.errorMessage()).isNotBlank();
        assertThat(rows.count(DEAL_FACTS)).as("строки факта не появляется").isZero();
        assertThat(rows.count(DEAL_AGGREGATES)).as("и строки агрегата тоже").isZero();
    }

    @Test
    @DisplayName("B11.9 — Длинный путь усекается, а не роняет запись следа")
    void anOversizedPathIsTruncatedInsteadOfLosingTheTrace() {
        String oversized = "/api/v1/statistics/" + "a".repeat(OVERSIZED_PATH_LENGTH);

        Answer answer = getAnonymously(oversized);

        assertThat(answer.status()).isEqualTo(401);
        String surface = String.valueOf(onlyDenial().get(SURFACE_COLUMN));
        assertThat(surface)
                .as("отказ не стирает собственного следа тем надёжнее, чем длиннее путь")
                .hasSize(SURFACE_WIDTH)
                .startsWith("GET /api/v1/statistics/");
    }

    @Test
    @DisplayName("B11.10 — Каждая попытка — своя строка, дедупа нет")
    void everyAttemptGetsItsOwnRowAndThereIsNoDeduplication() {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            assertThat(getAnonymously(question()).status()).isEqualTo(401);
        }

        List<Map<String, Object>> denials = denials();
        assertThat(denials)
                .as("схлопывание попыток занижало бы наблюдаемую частоту")
                .hasSize(ATTEMPTS);
        assertThat(denials).extracting(row -> row.get(INTERNAL_ID_COLUMN)).doesNotHaveDuplicates();
        assertThat(denials).extracting(row -> row.get(CREATED_COLUMN)).doesNotHaveDuplicates();
    }

    /**
     * Отказ записи выражен ОТСУТСТВИЕМ таблицы ({@link Rows#withoutTable}):
     * остановка контейнера базы дала бы ту же тропу ценой ожидания пула, а
     * поднять его обратно с теми же данными нечем.
     */
    @Test
    @DisplayName("B11.11 — Отказ записи следа ответа не меняет")
    void aFailureToWriteTheTraceDoesNotChangeTheResponse() {
        Integer logMark = AppLog.mark();

        rows.withoutTable(DENIALS_TABLE, () -> {
            Answer answer = getAnonymously(question());

            assertThat(answer.status())
                    .as("отказ доступа не превращается в 500 из-за невозможности "
                            + "записать след")
                    .isEqualTo(401);
            assertThat(answer.carriesErrorDto()).isTrue();
            assertThat(answer.errorCode()).isEqualTo(UNAUTHENTICATED);
        });

        assertThat(AppLog.alarmsSince(logMark))
                .as("отказ записи громкий, а не тихий: носителем служит журнал приложения")
                .anyMatch(record -> record.contains(WRITE_FAILURE));
        assertThat(rows.count(DENIALS_TABLE)).as("строки в базе нет").isZero();
    }

    /**
     * Вопрос к агрегатам, годный по всем осям: отвергать его обязан контур, а
     * не выборка.
     */
    private static String question() {
        return aggregatePath("grain", DEAL_GRAIN, "from", day(WINDOW), "to", day(0));
    }

    /** Все строки следа в порядке заведения. */
    private List<Map<String, Object>> denials() {
        return rows.all(DENIALS_TABLE, SURROGATE_COLUMN);
    }

    /** Единственная строка следа; иных быть не должно. */
    private Map<String, Object> onlyDenial() {
        List<Map<String, Object>> denials = denials();
        assertThat(denials).as("попытка была одна, и строка у неё одна").hasSize(1);
        return denials.getFirst();
    }

    /**
     * Имена рядов ВЫДАЧИ, принадлежащих самому сервису.
     *
     * <p>Имя ряда кончается там, где начинаются метки либо значение, — и
     * разбор поэтому берёт голову строки, а не строку целиком: иначе каждая
     * пара давала бы своё имя.
     *
     * @param scrape выдача экспозиции дословно
     */
    private static Set<String> ownSeriesOf(String scrape) {
        return scrape.lines()
                .filter(line -> line.startsWith(OWN_SERIES_PREFIX))
                .map(line -> line.split("[{ ]")[0])
                .collect(Collectors.toSet());
    }

    /**
     * Имена точек, которые актуатор объявляет открытыми.
     *
     * <p>Читаются ссылками его корня: перебор доказывает отсутствие названных
     * точек, а РАВЕНСТВО перечня двум именам — только выдача, которую
     * собирает сам актуатор. Путевой вариант точки живости
     * ({@code health-path}) сводится к её имени: второй ссылкой он остаётся
     * той же точкой.
     */
    @SuppressWarnings("unchecked")
    private Set<String> exposedEndpoints() {
        Map<String, Object> links = (Map<String, Object>) get(ACTUATOR_ROOT, TENANT)
                .asObject().get("_links");
        return links.keySet().stream()
                .filter(name -> isFalse("self".equals(name)))
                .map(name -> name.replace(PATH_LINK_SUFFIX, ""))
                .collect(Collectors.toSet());
    }
}
