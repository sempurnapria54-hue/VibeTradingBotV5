package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.asJson;
import static com.example.strategies.unit.validation.ValidationFixture.baseAppetite;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов у предмета — группа {@code U32} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * .claude/skills/test-design.md §«Единица — кейс, и у него четыре
 * обязательных поля»: отсутствие выхода — такой же выход).
 *
 * <p><b>Клейм отсутствия читается ДВУМЯ способами.</b> Утверждение о
 * ЧИСЛЕ или МНОЖЕСТВЕ коллабораторов — рефлексией по нестатическим
 * полям: «ни репозитория, ни логгера, ни реестра метрик» есть
 * утверждение о множестве типов, и текстом оно не читается. Утверждение
 * об отсутствии ИМЕНИ (вызов к соседу, чтение часов процесса) —
 * исполняемым телом исходника, за вычетом комментариев.
 *
 * <p>Прогон, на котором вызова не случилось, отсутствия способности не
 * устанавливает: он утверждает о проходе, а клетка — о классе.
 */
class ValidatorAbsenceTest {

    /** Дерево исходников владельца определений: базовый каталог прогона — модуль. */
    private static final Path SUBJECT_SOURCE = Path.of("src", "main", "java", "com", "example", "strategies",
            "domain", "validation", "StrategyDefinitionValidator.java");

    @Test
    @DisplayName("U32.1 — годное дерево: метод возвращается молча, перечня нарушений наружу нет")
    void u32_1_theCreateEntryPointReturnsNothing() {
        assertThat(violations(reference())).isEmpty();
        assertThat(entryPoint("validateCreate").getReturnType())
                .as("у точки входа нет возвращаемого значения: «пустой перечень» наружу не отдаётся")
                .isEqualTo(void.class);
    }

    @Test
    @DisplayName("U32.2 — ни одной записи в базу: полей-коллабораторов у предмета нет")
    void u32_2_theSubjectHoldsNoPersistenceCollaborator() {
        assertThat(collaboratorTypes())
                .as("репозиториев и сервисов данных у предмета нет ни одного поля")
                .isEmpty();
    }

    @Test
    @DisplayName("U32.3 — ни одного вызова к соседу: числа приходят аргументом, ссылки резолвит вызывающий")
    void u32_3_theSubjectCallsNoPeer() {
        assertThat(executableBody())
                .doesNotContain("RestClient")
                .doesNotContain("WebClient")
                .doesNotContain("RestTemplate");
    }

    @Test
    @DisplayName("U32.4 — ни строки лога и ни одной метрики: наблюдаемость отказа — его текст")
    void u32_4_theSubjectWritesNeitherLogsNorMetrics() {
        assertThat(collaboratorTypes()).isEmpty();
        assertThat(executableBody())
                .doesNotContain("Logger")
                .doesNotContain("MeterRegistry")
                .doesNotContain("@Slf4j");
    }

    @Test
    @DisplayName("U32.5 — входное дерево не изменяется: валидатор не нормализует и не дозаполняет")
    void u32_5_theInputTreeIsNeverMutated() {
        CreateStrategyApiRequest request = reference();
        tranche(bull(request)).setLevelCount(3);
        entryAction(bull(request)).setAllocationPercents(decimal("100"));
        String before = asJson(request);

        violations(request);

        assertThat(asJson(request))
                .as("после вызова объект запроса равен поданному")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("U32.6 — часов процесса не читает ни одна точка: у срока годности только разбор")
    void u32_6_theSubjectReadsNoClock() {
        assertThat(executableBody())
                .doesNotContain("Instant.now")
                .doesNotContain("LocalDateTime.now")
                .doesNotContain("System.currentTimeMillis")
                .doesNotContain("Clock");
    }

    @Test
    @DisplayName("U32.7 — годное дерево подано дважды: состояния между вызовами предмет не держит")
    void u32_7_theSubjectKeepsNoStateBetweenCalls() {
        CreateStrategyApiRequest request = reference();
        tranche(bull(request)).setLevelCount(2);

        List<String> first = violations(request, baseAppetite());
        List<String> second = violations(request, baseAppetite());

        assertThat(second).isEqualTo(first);
        assertThat(first).isNotEmpty();
    }

    /** Типы нестатических полей предмета — его коллабораторы. */
    private static List<Class<?>> collaboratorTypes() {
        return Arrays.stream(StrategyDefinitionValidator.class.getDeclaredFields())
                .filter(field -> isFalse(field.isSynthetic()))
                .filter(field -> isFalse(Modifier.isStatic(field.getModifiers())))
                .map(Field::getType)
                .toList();
    }

    private static Method entryPoint(String name) {
        return Arrays.stream(StrategyDefinitionValidator.class.getDeclaredMethods())
                .filter(method -> Objects.equals(method.getName(), name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("у предмета нет точки входа " + name));
    }

    /** Исполняемое тело исходника: текст за вычетом комментариев. */
    private static String executableBody() {
        if (Files.isRegularFile(SUBJECT_SOURCE)) {
            return stripComments(read(SUBJECT_SOURCE));
        }
        throw new IllegalStateException("исходник не найден: " + SUBJECT_SOURCE.toAbsolutePath());
    }

    private static String read(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
