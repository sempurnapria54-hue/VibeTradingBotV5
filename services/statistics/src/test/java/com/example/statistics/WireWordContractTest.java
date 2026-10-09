package com.example.statistics;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.statistics.integration.internal.event.EnvelopeReader;
import com.example.statistics.integration.internal.event.model.StatisticsEventMessage;
import com.example.statistics.persistence.repository.FactAggregateSourceRepository;
import com.example.statistics.persistence.service.AggregateSourceDataService;
import com.example.statistics.util.Constants;
import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Сквозная сверка СЛОВ провода: слово, которым нативный запрос агрегата
 * отбирает строки, обязано быть значением, которое пишет <b>построенный
 * публикатор</b>, а имя, под которым значение едет в содержимом, — именем,
 * которое читатель журнала ищет своими константами.
 *
 * <p><b>Зачем эта охрана существует.</b> Пин значения у писателя стои́т по
 * правилу «слово или символ» (.claude/rules/codestyle.md §«Пин значения,
 * пересекающего провод: слово или символ»), и символьный пин переименование
 * значения не ловит: писатель и его проба уезжают одной правкой, а предикат
 * агрегата лежит словом в этом сервисе и молча перестаёт совпадать — счётчик
 * встаёт на нуле, неотличимом от «таких событий не было». Правило называет
 * охрану у потребителя «другого класса» — сверку литералов его запроса со
 * значениями перечней, прочитанными у построенного публикатора, — и она здесь.
 * Одна проба покрывает все слова и не растёт с новым: новое слово в запросе
 * либо принадлежит известной колонке и сверяется, либо роняет пробу.
 *
 * <p><b>Три оси одного стыка — три метода.</b>
 * <ul>
 *   <li><b>Значения, записанные литералом запроса</b>
 *   ({@link #everyWordTheAggregateComparesIsAValueThePublisherWrites}).
 *   Литералы берутся из аннотаций {@code @Query} собранных репозиториев, а
 *   не из текста исходников: комментарий, называющий слово, в предмет не
 *   попадает. Значения — у перечней публикатора: доменные перечни общей
 *   библиотеки ({@link CoreEventType}, перечни {@link Deal}) лежат на пути
 *   этого модуля и берутся типом; ступень и критичность живут перечнями ядра
 *   ({@code HoldRung}, {@code AnomalyReport.Severity}), на пути их нет, и они
 *   читаются из исходников ядра — тем же способом, каким
 *   {@link WireFormContractTest} читает имена заголовков.
 *   <b>Мутация, которая её роняет:</b> переименование у писателя значения,
 *   по которому агрегат ветвится ({@code Deal.CloseOutcome.LIQUIDATION},
 *   {@code Deal.CloseReason.STOP_LOSS},
 *   {@code CoreEventType.HOLD_RAISED}, {@code HoldRung.HARD},
 *   {@code Severity.CRITICAL} и прочие), либо слово в запросе по колонке,
 *   для которой носитель значений пробе не объявлен.</li>
 *   <li><b>Слова, уезжающие ПАРАМЕТРОМ</b>
 *   ({@link #everyWordBoundAsAParameterIsWhatThePublisherWrites}). Литерала в
 *   тексте у них нет, и сверяется то, что потребитель подставляет в
 *   параметр, — у настоящего {@link AggregateSourceDataService}, — с кодами,
 *   которые ставит ручная поверхность ядра: ссылки {@code Constants.Hold.*}
 *   её сервиса, разрешённые по константам ядра. Константы ядра на пути
 *   модуля не лежат и читаются из исходников.
 *   <b>Мутация:</b> переименование значения
 *   {@code MANUAL_HALT_REQUESTED}/{@code MANUAL_HALT_CLEARED} у ядра, новый
 *   код у ручной поверхности, не приехавший в
 *   {@code Constants.ManualOperation.CODES}, либо параметр {@code in (:…)}
 *   без объявленной привязки.</li>
 *   <li><b>ИМЕНА компонентов</b>
 *   ({@link #theReaderFindsTheRadiusAndTheBranchingFieldsInEveryCarriedForm}).
 *   Для каждого несомого класса форма берётся у публикатора — какой метод
 *   маппера пишет класс ({@code CoreEventWriter}) и какую форму он строит
 *   ({@code CoreEventMessageMapper}), — её компоненты читаются из записи
 *   общей библиотеки, документ сериализуется Jackson'ом, как у писателя, и
 *   разбирается настоящим {@link EnvelopeReader}. Требуется, чтобы читатель
 *   достал биржевой счёт у ВСЕХ несомых классов, определение стратегии — у
 *   терминала, и поле каждого слова, по которому агрегат ветвится, — у
 *   класса, его несущего.
 *   <b>Мутация:</b> переименование компонента формы
 *   ({@code HoldRaisedMessage.exchangeAccountInternalId}, {@code rung},
 *   {@code code} и прочих) либо константы читателя
 *   ({@code Constants.RadiusFields}, {@code Constants.ContentFields}).</li>
 * </ul>
 * Четвёртый метод ({@link #everyCarriedClassIsAClassThePublisherWrites})
 * держит перечень несомых классов потребителя ({@code Constants.CarriedEvent})
 * внутри перечня, который публикатор пишет.
 *
 * <p><b>Охват письменных форм слова — назван, а не подразумевается.</b> Проба
 * разбирает сравнение колонки со словом ({@code =}, {@code <>}, {@code !=}),
 * перечень литералов ({@code [not] in ('A', 'B')}) и перечень-параметр
 * ({@code [not] in (:имя)}). Слово ЗАГЛАВНЫМИ в кавычках, не взятое ни одной
 * из этих форм (обратный порядок {@code 'X' = колонка}, {@code like},
 * {@code any(array[…])}), роняет пробу, а не пропускается.
 *
 * <p><b>Что проба НЕ мерит, и это названо.</b> Аннотаций переименования у
 * формы ({@code @JsonProperty}) она не видит — компоненты читаются по записи;
 * сегодня таких аннотаций у форм нет. Классы, которые публикатор пишет, а
 * статистика не несёт, не проверяются: их имён этот читатель не ищет. Сторона
 * периметра — своя проба у своего потребителя.
 */
class WireWordContractTest {

    /** Корень исходников ядра — публикатора журнальных фактов. */
    private static final Path CORE = Path.of("..", "..", "services", "trading-core", "src", "main", "java",
            "com", "example", "tradingcore");

    /** Формы содержимого в общей библиотеке. */
    private static final Path MESSAGE_FORMS = Path.of("..", "..", "services", "common", "model", "message",
            "src", "main", "java", "com", "example", "tradingbot", "message");

    private static final Path HOLD_RUNG = CORE.resolve(Path.of("domain", "safety", "HoldRung.java"));
    private static final Path ANOMALY_REPORT = CORE.resolve(Path.of("domain", "safety", "AnomalyReport.java"));
    private static final Path CORE_CONSTANTS = CORE.resolve(Path.of("util", "Constants.java"));
    private static final Path MANUAL_HALT_SERVICE =
            CORE.resolve(Path.of("domain", "safety", "ManualHaltService.java"));
    private static final Path CORE_EVENT_WRITER =
            CORE.resolve(Path.of("integration", "internal", "event", "CoreEventWriter.java"));
    private static final Path CORE_EVENT_MAPPER = CORE.resolve(Path.of("mapping", "CoreEventMessageMapper.java"));

    /** Собранные репозитории сервиса: их {@code @Query} и есть тексты запросов. */
    private static final String REPOSITORY_CLASSES =
            "classpath*:com/example/statistics/persistence/repository/**/*.class";

    /** Сравнение колонки со словом: {@code alias.column = 'WORD'}, также {@code <>} и {@code !=}. */
    private static final Pattern COMPARED_WORD =
            Pattern.compile("(?:\\b[a-z_]+\\.)?\\b([a-z_]+)\\s*(?:=|<>|!=)\\s*'([^']*)'");

    /** Перечень у колонки: {@code alias.column [not] in (…)}. */
    private static final Pattern LISTED =
            Pattern.compile("(?:\\b[a-z_]+\\.)?\\b([a-z_]+)\\s+(?:not\\s+)?in\\s*\\(([^)]*)\\)");

    /** Содержимое перечня — один параметр. */
    private static final Pattern PARAMETER_LIST = Pattern.compile("\\s*:([A-Za-z]\\w*)\\s*");

    /** Содержимое перечня — литералы через запятую. */
    private static final Pattern LITERAL_LIST = Pattern.compile("\\s*'[^']*'(?:\\s*,\\s*'[^']*')*\\s*");

    /** Литерал в кавычках. */
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    /** Слово провода: заглавные и подчёркивание — форма имени значения перечня. */
    private static final Pattern WORD = Pattern.compile("'([A-Z][A-Z0-9_]*)'");

    /** Имя константы перечня в начале фрагмента объявления. */
    private static final Pattern ENUM_CONSTANT = Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)");

    /** Объявление строковой константы. */
    private static final Pattern STRING_CONSTANT =
            Pattern.compile("static\\s+final\\s+String\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*\"([^\"]*)\"");

    /** Ссылка ручной поверхности на код ступени ядра. */
    private static final Pattern HOLD_CODE_REFERENCE = Pattern.compile("Constants\\.Hold\\.([A-Z][A-Z0-9_]*)");

    /** Запись события у писателя: класс и метод маппера, строящий его содержимое. */
    private static final Pattern WRITTEN_CLASS =
            Pattern.compile("CoreEventType\\.([A-Z_]+)\\s*,\\s*eventMessageMapper\\.(\\w+)\\s*\\(");

    /** Метод маппера и форма, которую он строит. */
    private static final Pattern MAPPER_METHOD = Pattern.compile("(\\w+Message)\\s+(\\w+)\\s*\\(");

    /** Параметр отбора ручной тропы у запроса зерна происшествий. */
    private static final String MANUAL_OPERATION_CODES = "manualOperationCodes";

    /**
     * Параметры-перечни, несущие слова провода: имя параметра → колонка, по
     * которой он отбирает.
     */
    private static final Map<String, String> WIRE_WORD_PARAMETERS = Map.of(MANUAL_OPERATION_CODES, "operation_code");

    /**
     * Параметры-перечни, слов провода НЕ несущие, — с причиной. Параметр вне
     * обоих перечней роняет пробу: непривязанное слово неотличимо от
     * несвязанного.
     */
    private static final Map<String, String> NOT_WIRE_WORD_PARAMETERS = Map.of(
            "topics", "тема подписки — имя из конфигурации самого потребителя, а не значение перечня производителя");

    private static final OffsetDateTime DAY_START = OffsetDateTime.parse("2026-09-10T00:00:00Z");
    private static final OffsetDateTime DAY_END = OffsetDateTime.parse("2026-09-11T00:00:00Z");
    private static final String TOPIC = "trading-core.facts";
    private static final String TENANT = "tenant-1";
    private static final String VALUE_PREFIX = "w-";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EnvelopeReader reader = new EnvelopeReader(objectMapper);

    @Test
    @DisplayName("U1.1 — Каждое слово, которым агрегат отбирает строки, — значение, которое пишет публикатор")
    void everyWordTheAggregateComparesIsAValueThePublisherWrites() throws Exception {
        Map<String, Set<String>> published = publishedValuesByColumn();
        List<String> compared = new ArrayList<>();

        for (Map.Entry<String, String> query : queryTexts().entrySet()) {
            String text = query.getValue();
            List<String> recognized = new ArrayList<>();
            Matcher comparison = COMPARED_WORD.matcher(text);
            while (comparison.find()) {
                checkWord(published, query.getKey(), comparison.group(1), comparison.group(2));
                recognized.add(comparison.group(2));
            }
            Matcher listing = LISTED.matcher(text);
            while (listing.find()) {
                if (LITERAL_LIST.matcher(listing.group(2)).matches()) {
                    Matcher literal = QUOTED.matcher(listing.group(2));
                    while (literal.find()) {
                        checkWord(published, query.getKey(), listing.group(1), literal.group(1));
                        recognized.add(literal.group(1));
                    }
                }
            }
            compared.addAll(recognized);
            Matcher word = WORD.matcher(text);
            while (word.find()) {
                if (isFalse(recognized.remove(word.group(1)))) {
                    fail("%s: слово '%s' стоит в форме, которую проба не разбирает — оно не сверено ни с чем",
                            query.getKey(), word.group(1));
                }
            }
        }

        assertThat(compared)
                .as("базовый гейт: ни одного слова в запросах не найдено — мерить нечего, а не «дефектов нет»")
                .isNotEmpty();
    }

    @Test
    @DisplayName("U1.2 — Слово, уезжающее параметром, — то, что ставит ручная поверхность публикатора")
    void everyWordBoundAsAParameterIsWhatThePublisherWrites() throws Exception {
        Set<String> parameters = new LinkedHashSet<>();
        for (Map.Entry<String, String> query : queryTexts().entrySet()) {
            Matcher listing = LISTED.matcher(query.getValue());
            while (listing.find()) {
                Matcher parameter = PARAMETER_LIST.matcher(listing.group(2));
                if (isFalse(parameter.matches())) {
                    continue;
                }
                String name = parameter.group(1);
                parameters.add(name);
                if (NOT_WIRE_WORD_PARAMETERS.containsKey(name)) {
                    continue;
                }
                if (isFalse(WIRE_WORD_PARAMETERS.containsKey(name))) {
                    fail("%s: параметр-перечень :%s не привязан ни к значениям публикатора, ни к причине, "
                            + "по которой слов провода он не несёт", query.getKey(), name);
                }
                assertThat(listing.group(1))
                        .as("%s: параметр :%s отбирает не ту колонку, к значениям которой привязан",
                                query.getKey(), name)
                        .isEqualTo(WIRE_WORD_PARAMETERS.get(name));
            }
        }
        assertThat(parameters)
                .as("базовый гейт: параметр ручной тропы в запросах не найден — мерить нечего")
                .contains(MANUAL_OPERATION_CODES);

        Method collect = FactAggregateSourceRepository.class.getMethod("collectIncidentGrain",
                OffsetDateTime.class, OffsetDateTime.class, Collection.class);
        assertThat(collect.getParameters()[2].getAnnotation(Param.class).value())
                .as("третий аргумент выборки зерна обязан быть параметром ручной тропы")
                .isEqualTo(MANUAL_OPERATION_CODES);

        assertThat(manualCodesBoundByTheConsumer())
                .as("коды, которые потребитель подставляет в отбор ручной тропы, обязаны совпасть с кодами, "
                        + "которые пишет ручная поверхность ядра, — иначе счётчики ручной тропы встают на нуле")
                .containsExactlyInAnyOrderElementsOf(manualCodesWrittenByThePublisher());
    }

    @Test
    @DisplayName("U1.3 — Каждый несомый класс — класс, который публикатор пишет")
    void everyCarriedClassIsAClassThePublisherWrites() throws IOException {
        Set<String> written = writtenForms().keySet();

        assertThat(names(CoreEventType.values()))
                .as("несомый класс статистики обязан быть значением перечня классов публикатора")
                .containsAll(carriedClasses());
        assertThat(written)
                .as("несомый класс статистики обязан писаться писателем событий ядра")
                .containsAll(carriedClasses());
    }

    @Test
    @DisplayName("U1.4 — Читатель достаёт радиус и поля слов из формы каждого несомого класса")
    void theReaderFindsTheRadiusAndTheBranchingFieldsInEveryCarriedForm() throws IOException {
        Map<String, String> forms = writtenForms();

        for (String eventType : carriedClasses()) {
            String form = forms.get(eventType);
            if (isNull(form)) {
                fail("класс %s публикатор не пишет — формы у него нет", eventType);
            }
            StatisticsEventMessage message = reader.read(recordOf(eventType, contentOf(form)));

            expectedReadings(eventType).forEach((field, reading) -> assertThat(reading.apply(message))
                    .as("%s (%s): читатель не нашёл «%s» — имя компонента формы и константа читателя разошлись",
                            eventType, form, field)
                    .isNotNull());
        }
    }

    // --- значения публикатора ---

    /**
     * Колонка факта → значения, которые публикатор пишет в поле, из которого
     * колонка заполняется. Колонка, которой здесь нет, роняет пробу на первом
     * же слове по ней.
     */
    private Map<String, Set<String>> publishedValuesByColumn() throws IOException {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        values.put("event_type", names(CoreEventType.values()));
        values.put("close_outcome", names(Deal.CloseOutcome.values()));
        values.put("close_reason", names(Deal.CloseReason.values()));
        values.put("reconciliation_status", names(Deal.ReconciliationStatus.values()));
        values.put("breakdown_incomplete", names(Deal.BreakdownCompleteness.values()));
        values.put("risk_benchmark_availability", names(Deal.RiskBenchmarkAvailability.values()));
        values.put("hold_rung", enumConstants(HOLD_RUNG, "HoldRung"));
        values.put("anomaly_severity", enumConstants(ANOMALY_REPORT, "Severity"));
        return values;
    }

    private void checkWord(Map<String, Set<String>> published, String query, String column, String word) {
        Set<String> values = published.get(column);
        if (isNull(values)) {
            fail("%s: колонка %s сравнивается со словом '%s', а носитель её значений у публикатора пробе не "
                    + "объявлен — впиши его в publishedValuesByColumn", query, column, word);
        }
        assertThat(values)
                .as("%s: слово '%s' по колонке %s не пишет ни одно значение публикатора — счётчик встанет на нуле",
                        query, word, column)
                .contains(word);
    }

    /** Коды, которые ставит ручная поверхность ядра: её ссылки на коды ступени, разрешённые по константам. */
    private Set<String> manualCodesWrittenByThePublisher() throws IOException {
        Map<String, String> holdCodes = new LinkedHashMap<>();
        Matcher constant = STRING_CONSTANT.matcher(block(code(CORE_CONSTANTS), "class\\s+Hold\\s*\\{"));
        while (constant.find()) {
            holdCodes.put(constant.group(1), constant.group(2));
        }
        Set<String> codes = new LinkedHashSet<>();
        Matcher reference = HOLD_CODE_REFERENCE.matcher(code(MANUAL_HALT_SERVICE));
        while (reference.find()) {
            String value = holdCodes.get(reference.group(1));
            if (isNull(value)) {
                fail("ручная поверхность ссылается на код ступени, которого у ядра нет: " + reference.group(1));
            }
            codes.add(value);
        }
        assertThat(codes)
                .as("базовый гейт: у ручной поверхности ядра не найдено ни одного кода — мерить нечего")
                .isNotEmpty();
        return codes;
    }

    /** Что потребитель на деле подставляет в параметр ручной тропы — у настоящего сервиса данных. */
    @SuppressWarnings("unchecked")
    private Set<String> manualCodesBoundByTheConsumer() {
        FactAggregateSourceRepository repository = mock(FactAggregateSourceRepository.class);
        ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.forClass(Collection.class);

        new AggregateSourceDataService(repository).collectIncidentGrain(DAY_START, DAY_END);

        verify(repository).collectIncidentGrain(eq(DAY_START), eq(DAY_END), codes.capture());
        return new LinkedHashSet<>(codes.getValue());
    }

    // --- тексты запросов ---

    /** Тексты {@code @Query} всех собранных репозиториев сервиса: «Класс#метод» → текст. */
    private Map<String, String> queryTexts() throws IOException, ClassNotFoundException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory readers = new SimpleMetadataReaderFactory(resolver);
        Map<String, String> texts = new TreeMap<>();
        for (Resource resource : resolver.getResources(REPOSITORY_CLASSES)) {
            String className = readers.getMetadataReader(resource).getClassMetadata().getClassName();
            Class<?> type = Class.forName(className, false, getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (nonNull(query)) {
                    texts.put(type.getSimpleName() + "#" + method.getName(), query.value());
                }
            }
        }
        assertThat(texts)
                .as("базовый гейт: ни одного запроса у репозиториев не найдено — мерить нечего")
                .isNotEmpty();
        return texts;
    }

    // --- формы публикатора ---

    /** Класс события → форма содержимого, которую для него строит маппер писателя. */
    private Map<String, String> writtenForms() throws IOException {
        Map<String, String> formByMethod = new LinkedHashMap<>();
        Matcher method = MAPPER_METHOD.matcher(code(CORE_EVENT_MAPPER));
        while (method.find()) {
            formByMethod.put(method.group(2), method.group(1));
        }
        Map<String, String> forms = new LinkedHashMap<>();
        Matcher written = WRITTEN_CLASS.matcher(code(CORE_EVENT_WRITER));
        while (written.find()) {
            String form = formByMethod.get(written.group(2));
            if (isNull(form)) {
                fail("писатель зовёт метод маппера, которого у маппера нет: " + written.group(2));
            }
            forms.put(written.group(1), form);
        }
        assertThat(forms)
                .as("базовый гейт: у писателя событий ядра не найдено ни одной записи класса — мерить нечего")
                .isNotEmpty();
        return forms;
    }

    /**
     * Содержимое, собранное компонентами формы так, как его сериализует
     * писатель: имя компонента — ключ верхнего уровня. Строка несёт своё
     * значение, флаг — истину, число — единицу; компонент иного типа (дерево
     * определения) в документ не кладётся — его имён читатель не ищет.
     */
    private String contentOf(String form) throws IOException {
        Matcher record = Pattern.compile("record\\s+" + form + "\\s*\\(([^)]*)\\)")
                .matcher(code(MESSAGE_FORMS.resolve(form + ".java")));
        if (isFalse(record.find())) {
            fail("форма %s не найдена записью в общей библиотеке", form);
        }
        ObjectNode content = objectMapper.createObjectNode();
        for (String component : record.group(1).split(",")) {
            String[] tokens = component.trim().split("\\s+");
            String type = tokens[tokens.length - 2];
            String name = tokens[tokens.length - 1];
            switch (type) {
                case "String" -> content.put(name, VALUE_PREFIX + name);
                case "Boolean" -> content.put(name, Boolean.TRUE);
                case "BigDecimal" -> content.put(name, BigDecimal.ONE);
                default -> {
                    // компонент-дерево читателю статистики не адресован
                }
            }
        }
        assertThat(content.size())
                .as("базовый гейт: у формы %s не разобрано ни одного компонента", form)
                .isPositive();
        return objectMapper.writeValueAsString(content);
    }

    private ConsumerRecord<String, String> recordOf(String eventType, String content) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, TENANT, content);
        record.headers().add(new RecordHeader(Constants.EventHeaders.EVENT_TYPE,
                eventType.getBytes(StandardCharsets.UTF_8)));
        return record;
    }

    /**
     * Что читатель обязан достать из формы класса: биржевой счёт — ключ обоих
     * зёрен, у всякого несомого класса; определение стратегии — ключ
     * сделочного зерна; поля, по словам которых агрегат ветвится, — у класса,
     * который их несёт.
     *
     * <p><b>Мера проскока выхода по стопу стои́т здесь же, хотя словом она не
     * является:</b> агрегат ветвится и по ней — по её НЕПУСТОТЕ
     * ({@code stopExitSlippageDeals}), — и имя, разошедшееся у формы и
     * читателя, дало бы пустую меру у каждой сделки: счётчик измеренных
     * встал бы на нуле, неотличимом от «не измерено ни разу».
     */
    private Map<String, Function<StatisticsEventMessage, Object>> expectedReadings(String eventType) {
        Map<String, Function<StatisticsEventMessage, Object>> readings = new LinkedHashMap<>();
        readings.put(Constants.RadiusFields.EXCHANGE_ACCOUNT_INTERNAL_ID,
                StatisticsEventMessage::getExchangeAccountInternalId);
        switch (eventType) {
            case Constants.CarriedEvent.DEAL_CLOSED -> {
                readings.put(Constants.RadiusFields.STRATEGY_INTERNAL_ID,
                        StatisticsEventMessage::getStrategyInternalId);
                readings.put(Constants.ContentFields.CLOSE_OUTCOME, StatisticsEventMessage::getCloseOutcome);
                readings.put(Constants.ContentFields.RECONCILIATION_STATUS,
                        StatisticsEventMessage::getReconciliationStatus);
                readings.put(Constants.ContentFields.BREAKDOWN_INCOMPLETE,
                        StatisticsEventMessage::getBreakdownIncomplete);
                readings.put(Constants.ContentFields.RISK_BENCHMARK_AVAILABILITY,
                        StatisticsEventMessage::getRiskBenchmarkAvailability);
                readings.put(Constants.ContentFields.CLOSE_REASON, StatisticsEventMessage::getCloseReason);
                readings.put(Constants.ContentFields.STOP_EXIT_SLIPPAGE, StatisticsEventMessage::getStopExitSlippage);
            }
            case Constants.CarriedEvent.HOLD_RAISED -> {
                readings.put(Constants.ContentFields.RUNG, StatisticsEventMessage::getHoldRung);
                readings.put(Constants.ContentFields.CODE, StatisticsEventMessage::getOperationCode);
            }
            case Constants.CarriedEvent.ANOMALY_REPORTED -> {
                readings.put(Constants.ContentFields.SEVERITY, StatisticsEventMessage::getAnomalySeverity);
                readings.put(Constants.ContentFields.CODE, StatisticsEventMessage::getOperationCode);
            }
            default -> {
                // у прочих несомых классов агрегат ветвится только по классу, а класс едет заголовком
            }
        }
        return readings;
    }

    private List<String> carriedClasses() {
        List<String> carried = new ArrayList<>();
        carried.add(Constants.CarriedEvent.DEAL_CLOSED);
        carried.addAll(Constants.CarriedEvent.INCIDENT_CLASSES);
        return carried;
    }

    // --- разбор исходников публикатора ---

    /** Исходник без комментариев: имя, названное в javadoc, в предмет не попадает. */
    private static String code(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\\n]*", "");
    }

    /** Тело блока, чей заголовок оканчивается открывающей скобкой. */
    private static String block(String source, String header) {
        Matcher start = Pattern.compile(header).matcher(source);
        if (isFalse(start.find())) {
            fail("блок не найден: " + header);
        }
        int depth = 1;
        int position = start.end();
        while (depth > 0 && position < source.length()) {
            char symbol = source.charAt(position);
            if (symbol == '{') {
                depth++;
            }
            if (symbol == '}') {
                depth--;
            }
            position++;
        }
        return source.substring(start.end(), position - 1);
    }

    /** Константы перечня, объявленного в исходнике. */
    private static Set<String> enumConstants(Path file, String enumName) throws IOException {
        String declarations = block(code(file), "enum\\s+" + enumName + "\\b[^{]*\\{").split(";", 2)[0];
        Set<String> constants = new LinkedHashSet<>();
        for (String declaration : declarations.split(",")) {
            Matcher constant = ENUM_CONSTANT.matcher(declaration);
            if (constant.find()) {
                constants.add(constant.group(1));
            }
        }
        assertThat(constants)
                .as("базовый гейт: у перечня %s в %s не разобрано ни одного значения", enumName, file)
                .isNotEmpty();
        return constants;
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
