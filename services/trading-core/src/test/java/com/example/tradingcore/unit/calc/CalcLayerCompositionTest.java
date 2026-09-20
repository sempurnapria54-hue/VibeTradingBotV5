package com.example.tradingcore.unit.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import com.example.tradingcore.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingcore.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingcore.domain.command.resolve.AttachedProtectionResolution;
import com.example.tradingcore.domain.command.resolve.PositionStatusResolver;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Состав классов: чего предмет не делает — группа {@code U14} документа
 * `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/components/PositionStatusResolver.md §Границы;
 * docs/components/AttachedAlgoOrderStateResolver.md §Границы;
 * docs/components/RetryPolicyService.md §Границы;
 * `.claude/rules/codestyle.md` §Слои).
 *
 * <p><b>Выход этой группы прогоном входов не наблюдается:</b> «ни одного
 * обращения к базе» есть утверждение об ОТСУТСТВИИ тропы, а отсутствие
 * тропы читается по коду — полями, импортами и исходником шести классов
 * (`.claude/rules/measurement-commands.md`). Красный прогон означает, что
 * единица обзавелась границей, которой у неё быть не должно.
 *
 * <p><b>Мерится исполняемое тело, а не текст файла:</b> комментарии
 * сняты, потому что javadoc законно называет соседей по имени — например,
 * дом политики либо границу, которой класс НЕ пользуется, — и проба
 * падала бы на упоминании, а не на тропе.
 */
class CalcLayerCompositionTest {

    private static final Path CALC = Path.of("src", "main", "java", "com", "example", "tradingcore",
            "domain", "command", "calc");
    private static final Path RESOLVE = Path.of("src", "main", "java", "com", "example",
            "tradingcore", "domain", "command", "resolve");
    private static final Path COMMAND = Path.of("src", "main", "java", "com", "example",
            "tradingcore", "domain", "command");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("^\\s*//.*$", Pattern.MULTILINE);

    /** Шесть классов предмета. */
    private static final List<Class<?>> SUBJECT = List.of(
            DealResultCalculator.class,
            DealReconciliationCalculator.class,
            DealTerminalFeaturesWriter.class,
            PositionStatusResolver.class,
            AttachedAlgoOrderStateResolver.class,
            RetryPolicyService.class);

    /** Исходники тех же шести классов. */
    private static final List<Path> SUBJECT_SOURCES = List.of(
            CALC.resolve("DealResultCalculator.java"),
            CALC.resolve("DealReconciliationCalculator.java"),
            CALC.resolve("DealTerminalFeaturesWriter.java"),
            RESOLVE.resolve("PositionStatusResolver.java"),
            RESOLVE.resolve("AttachedAlgoOrderStateResolver.java"),
            COMMAND.resolve("RetryPolicyService.java"));

    @Test
    @DisplayName("U14.1 — ни одного репозитория и ни одного DataService среди зависимостей")
    void u14_1_theCalcLayerNeverReachesTheDatabase() throws IOException {
        assertThat(sourcesMentioning("Repository", "DataService", "EntityManager", "JdbcTemplate",
                "@Transactional"))
                .as("граница domain ↔ persistence живёт у DataService, а расчётный слой к базе "
                        + "не ходит")
                .isEmpty();
    }

    @Test
    @DisplayName("U14.2 — ни одной публикации в тему и ни одного писателя фактов")
    void u14_2_theCalcLayerPublishesNothing() throws IOException {
        assertThat(sourcesMentioning("KafkaTemplate", "ProducerRecord", "CoreEventWriter",
                "EventWriter"))
                .as("событие терминала пишет исполнитель ребра, а не расчёт")
                .isEmpty();
    }

    @Test
    @DisplayName("U14.3 — граница у предмета ровно одна: журнал происшествий у писателя признаков")
    void u14_3_thereIsExactlyOneBoundary() {
        List<Class<?>> boundaries = SUBJECT.stream()
                .flatMap(CalcLayerCompositionTest::instanceFieldTypes)
                .filter(type -> Stream.of("Properties").noneMatch(type.getSimpleName()::endsWith))
                .filter(type -> SUBJECT.stream().noneMatch(type::equals))
                .toList();

        assertThat(boundaries)
                .as("у прочих пяти классов зависимостей-границ нет вовсе")
                .containsExactly(AnomalyReportService.class);
    }

    @Test
    @DisplayName("U14.4 — оба резолвера сущностей не сохраняют и решений статусной машины не принимают")
    void u14_4_theResolversNeitherPersistNorDecideTransitions() throws IOException {
        assertThat(sourcesMentioning(List.of(RESOLVE.resolve("PositionStatusResolver.java"),
                        RESOLVE.resolve("AttachedAlgoOrderStateResolver.java")),
                "StateMachine", "DataService", "Repository"))
                .isEmpty();

        assertThat(returnTypesOf(PositionStatusResolver.class))
                .as("отдают result-object, применяет исполнитель")
                .containsExactly(StatusResolveResult.class);
        assertThat(returnTypesOf(AttachedAlgoOrderStateResolver.class))
                .containsExactlyInAnyOrder(AttachedProtectionResolution.class, Boolean.class);
    }

    @Test
    @DisplayName("U14.5 — резолвер встроенной защиты ценовую базу триггера не сверяет")
    void u14_5_theAttachedResolverDoesNotVerifyTheTriggerPriceBase() throws IOException {
        assertThat(sourcesMentioning(List.of(RESOLVE.resolve("AttachedAlgoOrderStateResolver.java")),
                "triggerPriceType", "TriggerPriceType", "stopLossTriggerPrice"))
                .as("расхождение эха с объявленной базой — биржевой инвариант на РОДИТЕЛЬСКОЙ "
                        + "заявке, а не статус защиты")
                .isEmpty();
    }

    @Test
    @DisplayName("U14.6 — политика повтора исход повтора не интерпретирует")
    void u14_6_theRetryPolicyDoesNotInterpretTheOutcome() throws IOException {
        assertThat(sourcesMentioning(List.of(COMMAND.resolve("RetryPolicyService.java")),
                "ExchangeFailureClass", "RetryError", "Exception"))
                .as("классификации ошибок среди её зависимостей нет")
                .isEmpty();
    }

    @Test
    @DisplayName("U14.7 — пер-источниковых реализаций у обоих резолверов нет ни одной")
    void u14_7_thereAreNoPerSourceImplementations() throws IOException {
        assertThat(PositionStatusResolver.class.getInterfaces())
                .as("интерфейса с единственной реализацией предмет не заводит")
                .isEmpty();
        assertThat(AttachedAlgoOrderStateResolver.class.getInterfaces()).isEmpty();

        try (Stream<Path> files = Files.list(RESOLVE)) {
            assertThat(files.map(path -> path.getFileName().toString()).sorted())
                    .as("в пакете резолверов лежат ровно два резолвера и две их формы-значения")
                    .containsExactly("AttachedAlgoOrderStateResolver.java",
                            "AttachedProtectionFacts.java", "AttachedProtectionResolution.java",
                            "PositionStatusResolver.java");
        }
    }

    // ------------------------------------------------------------------
    // Чтение состава
    // ------------------------------------------------------------------

    /** Типы НЕстатических полей класса — его объявленные зависимости. */
    private static Stream<Class<?>> instanceFieldTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType);
    }

    /** Типы возврата публичных методов класса, объявленных им самим. */
    private static List<Class<?>> returnTypesOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getReturnType)
                .distinct()
                .toList();
    }

    private static List<String> sourcesMentioning(String... needles) throws IOException {
        return sourcesMentioning(SUBJECT_SOURCES, needles);
    }

    /** Исходники, чьё ИСПОЛНЯЕМОЕ тело упоминает хотя бы одно из имён. */
    private static List<String> sourcesMentioning(List<Path> sources, String... needles)
            throws IOException {
        List<String> found = new ArrayList<>();
        for (Path source : sources) {
            String body = executableBody(source);
            for (String needle : needles) {
                if (body.contains(needle)) {
                    found.add(source.getFileName() + " → " + needle);
                }
            }
        }
        return found;
    }

    /** Тело исходника за вычетом блочных и строчных комментариев. */
    private static String executableBody(Path source) throws IOException {
        String text = Files.readString(source, StandardCharsets.UTF_8);
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(text).replaceAll(""))
                .replaceAll("");
    }
}
