package com.example.testsupport;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Потоков у планировщика сервиса не меньше, чем джоб у него самого
 * (.claude/rules/codestyle.md §Джобы, клауза «Операнда живости мало: такт
 * обязан ДОСТАТЬСЯ тику»).
 *
 * <p><b>Зачем проба вообще нужна.</b> Число потоков и число джоб лежат в
 * разных файлах, и без пробы держались бы они <b>арифметическим
 * совпадением</b>: следующая джоба, заведённая без правки пула, вернула бы
 * голодание — и вернула бы его <b>молча</b>, потому что отказа при этом не
 * происходит. Голодает при умолчании каркаса ИЗМЕРИТЕЛЬ, то есть джоба, чей
 * выход — измерение о чужом состоянии; её молчание читателю неотличимо от
 * состояния наблюдаемого.
 *
 * <p><b>Форма общая, а проба у каждого сервиса своя.</b> Наследник объявляет
 * себя в модуле сервиса, и относительные пути ниже разрешаются от каталога
 * ЭТОГО модуля — то есть каждая проба мерит своё дерево и свою конфигурацию.
 * Перечня сервисов у формы нет намеренно: рукописный перечень был бы вторым
 * носителем и разошёлся бы с деревом на первом же новом сервисе.
 *
 * <p><b>Почему общий носитель, а не третья копия.</b> До третьего
 * сервиса-измерителя копия была дешевле общего артефакта, и это было записано
 * у копий условием возврата. Условие сработало: третьим стало торговое ядро.
 *
 * <p><b>Счёт берётся из дерева, а не пишется рядом.</b> Перечень джоб,
 * написанный в пробе руками, разошёлся бы с деревом на первой же новой
 * джобе — то есть ровно тогда, когда проба и нужна.
 *
 * <p><b>Что проба НЕ мерит, и это названо.</b> Она не мерит, что джобы,
 * получив разные потоки, не конфликтуют за общие строки: разведение пар —
 * предмет чтения, а не прогона, и исход каждой пары записан у её дома. И она
 * не поднимает контекста самого сервиса: {@code @SpringBootTest} у модулей
 * нет намеренно — доезд числа мерится на том же автоконфиге, каким
 * планировщик собирает Boot.
 *
 * <p><b>Пробы объявлены {@code protected}, а не пакетно.</b> Наследник живёт
 * в пакете своего сервиса, и пакетная видимость сделала бы обнаружение
 * методов зависимым от совпадения пакетов — то есть проба молчала бы, не
 * отказывая.
 *
 * <p><b>Две пробы каркаса исполняются в каждом модуле, и цена принята.</b>
 * Доезд имени ключа и умолчание в один поток — факты каркаса, а не сервиса;
 * повторный их прогон стои́т миллисекунд, зато носитель у формы один, и
 * расхождения копий не бывает по построению.
 */
public abstract class SchedulerCapacityContract {

    /** Дерево, в котором живут джобы сервиса. */
    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** Конфигурация сервиса — дом объявленного числа потоков. */
    private static final Path SERVICE_CONFIG =
            Path.of("src", "main", "resources", "application.yaml");

    private static final String POOL_SIZE_PROPERTY = "spring.task.scheduling.pool.size";

    /**
     * Объявление джобы опознаётся <b>с начала строки</b>: в javadoc та же
     * форма стои́т за звёздочкой, и без этой границы проба считала бы
     * упоминания класса наравне с его членами.
     */
    private static final Pattern SCHEDULED_DECLARATION = Pattern.compile("^\\s*@Scheduled\\b");

    /**
     * Число пробы доезда намеренно не равно ни умолчанию каркаса, ни
     * объявленному в конфигурации какого-либо сервиса: при совпадении
     * «величина доехала» было бы неотличимо от «планировщик взял своё
     * умолчание».
     */
    private static final int PROBE_POOL_SIZE = 11;

    /** Умолчание каркаса, из которого выведена сама надобность объявления. */
    private static final int FRAMEWORK_DEFAULT_POOL_SIZE = 1;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingEnabled.class)
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class));

    @Test
    @DisplayName("Потоков планировщика не меньше, чем @Scheduled-методов у сервиса")
    protected void everyScheduledMethodOfTheServiceHasAThreadOfItsOwn() throws IOException {
        Integer jobs = scheduledDeclarationCount();

        assertThat(jobs)
                .as("объявлений @Scheduled в дереве не найдено — мерить нечего, а не «всё сошлось»")
                .isPositive();
        assertThat(declaredPoolSize())
                .as("пул планировщика выводится из числа джоб: при меньшем числе потоков "
                        + "длинный проход держит такт соседа, и голодает в том числе измеритель")
                .isGreaterThanOrEqualTo(jobs);
    }

    /**
     * Ключ конфигурации — тот самый, который читает каркас: опечатка в его
     * имени не отказывает, а <b>молча</b> оставляет умолчание.
     */
    @Test
    @DisplayName("Объявленное число потоков доезжает до планировщика")
    protected void theDeclaredNumberOfThreadsReachesTheScheduler() {
        runner.withPropertyValues(POOL_SIZE_PROPERTY + "=" + PROBE_POOL_SIZE).run(context ->
                assertThat(corePoolSizeOf(context.getBean(ThreadPoolTaskScheduler.class)))
                        .isEqualTo(PROBE_POOL_SIZE));
    }

    /** Умолчание каркаса — один поток на все джобы; на нём стои́т сам вывод. */
    @Test
    @DisplayName("Без объявления планировщик берёт один поток на все джобы")
    protected void withoutTheDeclarationTheSchedulerTakesASingleThread() {
        runner.run(context ->
                assertThat(corePoolSizeOf(context.getBean(ThreadPoolTaskScheduler.class)))
                        .as("если умолчание каркаса перестало быть единицей, вывод числа "
                                + "потерял свою посылку и обязан быть пересмотрен")
                        .isEqualTo(FRAMEWORK_DEFAULT_POOL_SIZE));
    }

    /**
     * Назначенное число потоков читается ЯДРОМ пула, а не его текущим
     * размером: потоки создаются лениво, и до первой задачи пул любого
     * назначенного размера отвечает нулём.
     */
    private Integer corePoolSizeOf(ThreadPoolTaskScheduler scheduler) {
        return scheduler.getScheduledThreadPoolExecutor().getCorePoolSize();
    }

    /** Сколько объявлений джоб лежит в дереве сервиса. */
    private Integer scheduledDeclarationCount() throws IOException {
        assertThat(Files.isDirectory(MAIN_SOURCES))
                .as("дерева исходников не найдено — проба мерила бы пустоту")
                .isTrue();
        int declarations = 0;
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            Iterator<Path> javaFiles = sources.filter(this::isJavaFile).iterator();
            while (javaFiles.hasNext()) {
                declarations += declarationsIn(javaFiles.next());
            }
        }
        return declarations;
    }

    private Boolean isJavaFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java");
    }

    private Integer declarationsIn(Path source) throws IOException {
        int declarations = 0;
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            if (SCHEDULED_DECLARATION.matcher(line).find()) {
                declarations++;
            }
        }
        return declarations;
    }

    /**
     * Число потоков, объявленное конфигурацией сервиса. Читается тем же
     * загрузчиком, каким конфигурацию читает Boot: разбор регулярным
     * выражением сверял бы текст, а не свойство, и имя ключа осталось бы
     * непроверенным.
     */
    private Integer declaredPoolSize() throws IOException {
        assertThat(Files.isRegularFile(SERVICE_CONFIG))
                .as("конфигурации сервиса не найдено — проба мерила бы пустоту")
                .isTrue();
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("service-config", new FileSystemResource(SERVICE_CONFIG));
        for (PropertySource<?> source : loaded) {
            Object declared = source.getProperty(POOL_SIZE_PROPERTY);
            if (nonNull(declared)) {
                return Integer.parseInt(String.valueOf(declared));
            }
        }
        return fail("конфигурация сервиса не объявляет %s — пул остался бы умолчанием каркаса",
                POOL_SIZE_PROPERTY);
    }

    /**
     * Обработчик расписания в контексте пробы: без него автоконфигурация
     * планировщика условна и бина не заводит вовсе.
     */
    @EnableScheduling
    @Configuration(proxyBeanMethods = false)
    static class SchedulingEnabled {
    }
}
