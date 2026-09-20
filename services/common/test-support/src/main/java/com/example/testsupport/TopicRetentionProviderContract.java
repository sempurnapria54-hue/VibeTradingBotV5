package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Применённый брокером срок хранения темы и ветви его пустоты — группа
 * `U12` и клетки `U15.2`, `U16.6` документа
 * `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии</b> (`.claude/rules/carrier-levels.md`): провайдер лежит двумя
 * дословными экземплярами.
 *
 * <p><b>Пустота здесь — единственная форма «не добыл».</b> Отказ вызова,
 * истёкший срок ожидания, прерывание, неотданное значение и значение, не
 * выражающее конечного срока, дают один и тот же исход, и ни один из них
 * не роняет тик (docs/components/ReceptionStateJob.md §«Не добыл — отдаёт
 * пустое, а не прежнее»).
 */
public abstract class TopicRetentionProviderContract {

    /** Тема пары и её соседка: у разных тем срок может различаться. */
    protected static final String TOPIC = "trading-core.facts";
    protected static final String NEIGHBOUR_TOPIC = "strategies.facts";

    /** Срок ожидания ответа брокера — тот же, что у добытчика. */
    private static final long CALL_TIMEOUT_MS = 5_000L;

    private static final String WEEK_MS = "604800000";

    // --- порты к своей копии ---------------------------------------------

    /** Срок хранения темы, как его добывает копия своего дерева. */
    protected abstract Optional<Long> retentionMs(Admin admin, String topic);

    /** Класс провайдера своего дерева. */
    protected abstract Class<?> topicRetentionProviderType();

    // --- U12: применённое значение и ветви пустоты -------------------------

    @Test
    @DisplayName("U12.1 — брокер отдал конечный срок: он и отдаётся, без пересчёта")
    void u12_1_anAppliedRetentionIsReturnedAsIs() {
        assertThat(retentionMs(brokerReturning(WEEK_MS), TOPIC))
                .as("носитель числа один: манифест применяет владелец темы, брокер отдаёт применённое")
                .contains(604_800_000L);
    }

    @Test
    @DisplayName("U12.2 — значение унаследовано от умолчания брокера: провенанс роли не играет")
    void u12_2_anInheritedValueIsStillTheAppliedOne() {
        ConfigEntry inherited = new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, WEEK_MS,
                ConfigEntry.ConfigSource.DEFAULT_CONFIG, false, false, List.of(),
                ConfigEntry.ConfigType.LONG, "");

        assertThat(retentionMs(brokerReturning(new Config(List.of(inherited))), TOPIC))
                .as("применимый — тот, который брокер ПРИМЕНЯЕТ, а не тот, который задан манифестом")
                .contains(604_800_000L);
    }

    @Test
    @DisplayName("U12.3 — вызов к брокеру отказал: пусто, умолчания не подставляется")
    void u12_3_aRefusedCallYieldsAbsence() {
        Admin admin = mock(Admin.class);
        when(admin.describeConfigs(anyCollection())).thenThrow(new KafkaException("связи с брокером нет"));

        assertThat(retentionMs(admin, TOPIC))
                .as("умолчание не бывает благоприятным: порог не выводится, и алерт срабатывает")
                .isEmpty();
    }

    @Test
    @DisplayName("U12.4 — ответ не уложился в срок ожидания: тот же исход, что у отказа")
    void u12_4_aTimedOutCallYieldsAbsence() throws Exception {
        assertThat(retentionMs(brokerFailingWith(new TimeoutException("брокер не ответил")), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U12.5 — ожидание прервано: пусто, и признак прерывания восстановлен")
    void u12_5_anInterruptedWaitRestoresTheFlag() throws Exception {
        Admin admin = brokerFailingWith(new InterruptedException("ожидание прервано"));

        Optional<Long> retention = retentionMs(admin, TOPIC);

        assertThat(retention).isEmpty();
        assertThat(Thread.interrupted())
                .as("проглоченный признак прерывания оставил бы поток неостановимым")
                .isTrue();
    }

    @Test
    @DisplayName("U12.6 — записи `retention.ms` в конфигурации нет: пусто")
    void u12_6_anAbsentEntryYieldsAbsence() {
        assertThat(retentionMs(brokerReturning(new Config(List.of())), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U12.7 — `retention.ms` пустой строкой: пусто")
    void u12_7_aBlankEntryYieldsAbsence() {
        assertThat(retentionMs(brokerReturning(""), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U12.8 — `retention.ms` равен -1: хранение без предела конечным сроком не является")
    void u12_8_anInfiniteRetentionIsNotAFiniteTerm() {
        assertThat(retentionMs(brokerReturning("-1"), TOPIC))
                .as("доля от бесконечности порогом не бывает")
                .isEmpty();
    }

    @Test
    @DisplayName("U12.10 — нечисловое значение: пусто, исключение наружу не уходит")
    void u12_10_anUnparseableValueYieldsAbsenceWithoutThrowing() {
        assertThatCode(() -> assertThat(retentionMs(brokerReturning("на неделю"), TOPIC)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U12.11 — две темы: у каждой спрашивается своя конфигурация")
    void u12_11_eachTopicIsAskedForItsOwnConfiguration() {
        Map<String, String> byTopic = new LinkedHashMap<>();
        byTopic.put(TOPIC, WEEK_MS);
        byTopic.put(NEIGHBOUR_TOPIC, "3600000");
        Admin admin = brokerReturningPerTopic(byTopic);

        assertThat(retentionMs(admin, TOPIC)).contains(604_800_000L);
        assertThat(retentionMs(admin, NEIGHBOUR_TOPIC))
                .as("значения разных тем не смешиваются")
                .contains(3_600_000L);
    }

    @Test
    @DisplayName("U12.12 — ресурс запроса — тема, а не брокер и не группа")
    void u12_12_theAskedResourceIsTheTopic() {
        RecordingAdmin admin = new RecordingAdmin(WEEK_MS);

        retentionMs(admin.admin(), TOPIC);

        assertThat(admin.resources)
                .as("срок у брокера спрашивается по имени темы: у разных тем он разный")
                .containsExactly(new ConfigResource(ConfigResource.Type.TOPIC, TOPIC));
    }

    @Test
    @DisplayName("U12.13 — пустой ответ: прежнее значение не возвращается")
    void u12_13_anEmptyAnswerDoesNotFallBackToThePreviousOne() {
        Admin admin = brokerReturning(WEEK_MS);
        assertThat(retentionMs(admin, TOPIC)).contains(604_800_000L);

        assertThat(retentionMs(brokerReturning(new Config(List.of())), TOPIC))
                .as("кеша у провайдера нет: прежнее значение утверждало бы измеренное там, где не измеряли")
                .isEmpty();
    }

    @Test
    @DisplayName("U12.14 — у копий провайдера нет ни одного объявленного различия")
    void u12_14_theProviderCopiesDeclareNoDifference() {
        assertThat(topicRetentionProviderType().getSimpleName()).isEqualTo("TopicRetentionProvider");
        assertThat(publicMethodNames(topicRetentionProviderType()))
                .as("поверхность у копий одна: срок хранения названной темы")
                .containsExactly("retentionMs");
    }

    // --- U15.2, U16.6 -----------------------------------------------------

    @Test
    @DisplayName("U15.2 — ветви пустоты у копий дают один и тот же исход")
    void u15_2_bothCopiesAgreeOnEveryAbsenceBranch() {
        assertThat(retentionMs(brokerReturning(new Config(List.of())), TOPIC)).isEmpty();
        assertThat(retentionMs(brokerReturning(""), TOPIC)).isEmpty();
        assertThat(retentionMs(brokerReturning("-1"), TOPIC)).isEmpty();
        assertThat(retentionMs(brokerReturning("на неделю"), TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("U16.6 — провайдер ничего не кеширует: единственное его поле — админ-клиент")
    void u16_6_theProviderKeepsNoStateOfItsOwn() {
        List<String> fields = new ArrayList<>();
        for (Field field : topicRetentionProviderType().getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                fields.add(field.getName());
            }
        }

        assertThat(fields)
                .as("хранимая копия срока вернула бы второй носитель числа")
                .containsExactly("admin");
    }

    // --- оснастка ---------------------------------------------------------

    /** Имена публичных методов класса — профиль его поверхности. */
    private static List<String> publicMethodNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static Admin brokerReturning(String retentionMs) {
        return brokerReturning(new Config(List.of(
                new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, retentionMs))));
    }

    private static Admin brokerReturning(Config config) {
        return brokerReturningPerResource(resource -> KafkaFuture.completedFuture(config));
    }

    private static Admin brokerReturningPerTopic(Map<String, String> retentionByTopic) {
        return brokerReturningPerResource(resource -> KafkaFuture.completedFuture(new Config(List.of(
                new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, retentionByTopic.get(resource.name()))))));
    }

    @SuppressWarnings("unchecked")
    private static Admin brokerFailingWith(Exception failure) throws Exception {
        KafkaFuture<Config> future = mock(KafkaFuture.class);
        when(future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenThrow(failure);
        return brokerReturningPerResource(resource -> future);
    }

    private static Admin brokerReturningPerResource(Function<ConfigResource, KafkaFuture<Config>> answers) {
        Admin admin = mock(Admin.class);
        when(admin.describeConfigs(anyCollection())).thenAnswer(invocation -> {
            Collection<ConfigResource> asked = invocation.getArgument(0);
            Map<ConfigResource, KafkaFuture<Config>> values = new LinkedHashMap<>();
            for (ConfigResource resource : asked) {
                values.put(resource, answers.apply(resource));
            }
            DescribeConfigsResult result = mock(DescribeConfigsResult.class);
            when(result.values()).thenReturn(values);
            return result;
        });
        return admin;
    }

    /** Админ-клиент, запоминающий, о каком ресурсе его спросили. */
    protected static final class RecordingAdmin {

        private final List<ConfigResource> resources = new ArrayList<>();
        private final String retentionMs;

        private RecordingAdmin(String retentionMs) {
            this.retentionMs = retentionMs;
        }

        private Admin admin() {
            return brokerReturningPerResource(resource -> {
                resources.add(resource);
                return KafkaFuture.completedFuture(new Config(List.of(
                        new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, retentionMs))));
            });
        }
    }
}
