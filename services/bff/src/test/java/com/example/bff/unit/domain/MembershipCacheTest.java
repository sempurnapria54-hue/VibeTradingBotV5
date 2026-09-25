package com.example.bff.unit.domain;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.MembershipCache;
import com.example.bff.domain.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Кэш членств: годность, промах и вытеснение — группа `U3` документа
 * `.claude/tests/cases/bff-perimeter-logic.md`
 * (docs/architecture/contracts.md §«Состояния периметр не держит»).
 *
 * <p><b>Почему это стои́т проверять.</b> Направление ошибки у кэша
 * названо разрешающим: отозванный участник сохраняет доступ до истечения
 * срока. Поэтому дефект здесь не падает, а ПРОДЛЕВАЕТ — запись, которая
 * не перечитывается, держит чужой контекст ровно столько, сколько её не
 * трогают, и наблюдать это по поверхности нечем.
 *
 * <p><b>Вытеснение читается картой, а не поверхностью:</b> просроченная
 * запись и отсутствующая для читателя неотличимы, и «карта не растёт»
 * иначе не наблюдается вовсе. Цена названа — кейсы `U3.8` и `U3.9` пинят
 * имя поля.
 *
 * <p><b>Срок годности ставится в ДАННЫХ, а не подменой часов:</b> предмет
 * читает часы процесса и операндом их не принимает, поэтому границу
 * приходится дожидаться.
 */
class MembershipCacheTest {

    private static final String SUBJECT = "user-42";
    private static final String OTHER_SUBJECT = "user-43";
    private static final TenantContext CONTEXT = new TenantContext("tenant-7", "OWNER");
    private static final TenantContext OTHER_CONTEXT = new TenantContext("tenant-8", "TRADER");

    /** Срок, за который кейс успевает шагнуть: достаточно мал, чтобы его дождаться. */
    private static final Duration SHORT_TTL = Duration.ofMillis(20);

    /** Срок, заведомо больший длительности кейса. */
    private static final Duration LONG_TTL = Duration.ofMinutes(10);

    private final PerimeterProperties properties = new PerimeterProperties();
    private final MembershipCache cache = new MembershipCache(properties);
    private final AtomicInteger calls = new AtomicInteger();

    /** Промах зовёт добытчика и сохраняет его ответ. */
    @Test
    @DisplayName("U3.1 — промах зовёт добытчика ровно раз и сохраняет запись")
    void u3_1_aMissCallsTheResolverOnceAndStoresTheEntry() {
        givenTtl(LONG_TTL);

        TenantContext resolved = cache.get(SUBJECT, resolverOf(CONTEXT));

        assertThat(resolved).as("отдаётся значение добытчика").isEqualTo(CONTEXT);
        assertThat(calls.get()).as("добытчик зовётся ровно раз").isEqualTo(1);
        cache.get(SUBJECT, resolverOf(CONTEXT));
        assertThat(calls.get()).as("запись сохранена: второй вызов добытчика не зовёт").isEqualTo(1);
    }

    /** Годная запись владельца не переспрашивает — ради этого кэш и заведён. */
    @Test
    @DisplayName("U3.2 — годная запись отдаётся без вызова добытчика")
    void u3_2_aFreshEntryIsServedWithoutTheResolver() {
        givenTtl(LONG_TTL);
        TenantContext first = cache.get(SUBJECT, resolverOf(CONTEXT));

        TenantContext second = cache.get(SUBJECT, resolverOf(OTHER_CONTEXT));

        assertThat(calls.get()).as("добытчик не зовётся ни разу сверх промаха").isEqualTo(1);
        assertThat(second).as("отдаётся то же самое сохранённое значение").isSameAs(first);
    }

    /** За сроком годности контекст перечитывается, и наружу уходит новый. */
    @Test
    @DisplayName("U3.3 — за сроком годности запись перечитывается, и прежняя наружу не уходит")
    void u3_3_anExpiredEntryIsRereadAndTheOldOneIsNotServed() {
        givenTtl(SHORT_TTL);
        cache.get(SUBJECT, resolverOf(CONTEXT));
        waitPast(Instant.now().plus(SHORT_TTL));
        givenTtl(LONG_TTL);

        TenantContext reread = cache.get(SUBJECT, resolverOf(OTHER_CONTEXT));

        assertThat(calls.get()).as("добытчик зовётся снова").isEqualTo(2);
        assertThat(reread).as("отдаётся новое значение").isEqualTo(OTHER_CONTEXT);
    }

    /** Ключ — субъект: значения двух предъявителей не смешиваются. */
    @Test
    @DisplayName("U3.4 — два субъекта получают каждый своё")
    void u3_4_twoSubjectsGetTheirOwnContexts() {
        givenTtl(LONG_TTL);

        TenantContext first = cache.get(SUBJECT, resolverOf(CONTEXT));
        TenantContext second = cache.get(OTHER_SUBJECT, resolverOf(OTHER_CONTEXT));

        assertThat(calls.get()).as("добытчик зовётся дважды — ключи разные").isEqualTo(2);
        assertThat(first).isEqualTo(CONTEXT);
        assertThat(second).isEqualTo(OTHER_CONTEXT);
    }

    /**
     * Отказ добытчика не сохраняется: записи не заводится, и следующий
     * вызов идёт к владельцу снова. Дом ветви отказа молчит — находка `F3`.
     */
    @Test
    @DisplayName("U3.5 — отказ добытчика проходит наружу и записи не заводит")
    void u3_5_aResolverFailurePassesThroughAndStoresNothing() {
        givenTtl(LONG_TTL);

        assertThatThrownBy(() -> cache.get(SUBJECT, failingResolver()))
                .as("исключение проходит наружу как есть")
                .isInstanceOf(IllegalStateException.class);

        cache.get(SUBJECT, resolverOf(CONTEXT));
        assertThat(calls.get()).as("записи не осталось: следующий вызов снова идёт к добытчику").isEqualTo(2);
        assertThat(entriesOf()).as("в карте лежит только удавшийся резолв").containsOnlyKeys(SUBJECT);
    }

    /** Нулевой срок годности делает промахом всякий вызов. */
    @Test
    @DisplayName("U3.6 — при нулевом сроке годности каждый вызов промах")
    void u3_6_aZeroTtlMakesEveryCallAMiss() {
        givenTtl(Duration.ZERO);

        cache.get(SUBJECT, resolverOf(CONTEXT));
        cache.get(SUBJECT, resolverOf(CONTEXT));
        cache.get(SUBJECT, resolverOf(CONTEXT));

        assertThat(calls.get()).as("годность — строгое «позже», и ноль её не даёт").isEqualTo(3);
    }

    /** Карта чистится на промахе, и только чужое просроченное. */
    @Test
    @DisplayName("U3.8 — промах вытесняет просроченные записи чужих субъектов")
    void u3_8_aMissEvictsTheExpiredEntriesOfOtherSubjects() {
        givenTtl(SHORT_TTL);
        cache.get(SUBJECT, resolverOf(CONTEXT));
        cache.get(OTHER_SUBJECT, resolverOf(OTHER_CONTEXT));
        waitPast(Instant.now().plus(SHORT_TTL));
        givenTtl(LONG_TTL);

        cache.get("user-44", resolverOf(CONTEXT));

        assertThat(entriesOf())
                .as("просроченные ушли тем же вызовом, только что поставленная осталась")
                .containsOnlyKeys("user-44");
    }

    /** На попаданиях карта не чистится вовсе. */
    @Test
    @DisplayName("U3.9 — попадания просроченных записей не вытесняют")
    void u3_9_hitsDoNotEvictAnything() {
        givenTtl(LONG_TTL);
        cache.get(SUBJECT, resolverOf(CONTEXT));
        givenTtl(SHORT_TTL);
        cache.get(OTHER_SUBJECT, resolverOf(OTHER_CONTEXT));
        waitPast(Instant.now().plus(SHORT_TTL));
        givenTtl(LONG_TTL);

        cache.get(SUBJECT, resolverOf(CONTEXT));
        cache.get(SUBJECT, resolverOf(CONTEXT));

        assertThat(entriesOf())
                .as("карта чистится только на промахе — просроченная чужая запись цела")
                .containsOnlyKeys(SUBJECT, OTHER_SUBJECT);
    }

    /**
     * Замка у кэша нет: одновременные промахи одного ключа зовут добытчика
     * каждый. Точка между чтением карты и вызовом добытчика снаружи не
     * наблюдаема, поэтому одновременность ставится самим добытчиком — он
     * не отвечает, пока не подошёл второй поток (дом пробела — `G2`).
     */
    @Test
    @DisplayName("U3.10 — два одновременных промаха одного ключа зовут добытчика дважды")
    void u3_10_twoConcurrentMissesCallTheResolverTwice() throws Exception {
        givenTtl(LONG_TTL);
        CountDownLatch bothArrived = new CountDownLatch(2);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            List<Future<TenantContext>> answers = threads.invokeAll(List.of(
                    () -> cache.get(SUBJECT, meetingResolver(bothArrived, CONTEXT)),
                    () -> cache.get(SUBJECT, meetingResolver(bothArrived, OTHER_CONTEXT))));

            assertThat(calls.get()).as("замка нет — добытчик позван каждым потоком").isEqualTo(2);
            assertThat(List.of(answers.get(0).get(), answers.get(1).get()))
                    .as("наружу уходят оба значения")
                    .containsExactlyInAnyOrder(CONTEXT, OTHER_CONTEXT);
            assertThat(cache.get(SUBJECT, resolverOf(CONTEXT)))
                    .as("в карте осталось записанное последним — одно из двух")
                    .isIn(CONTEXT, OTHER_CONTEXT);
            assertThat(calls.get()).as("и оно годно: третий вызов добытчика не зовёт").isEqualTo(2);
        } finally {
            threads.shutdownNow();
        }
    }

    /**
     * Пустоту от значения кэш не отличает. Состояние недостижимо:
     * единственный писатель добытчика либо бросает, либо возвращает
     * построенный контекст.
     */
    @Test
    @DisplayName("U3.11 — пустое значение добытчика кладётся записью и отдаётся попаданием")
    void u3_11_anEmptyResolvedValueIsStoredAsAnEntry() {
        givenTtl(LONG_TTL);

        TenantContext first = cache.get(SUBJECT, resolverOf(null));
        TenantContext second = cache.get(SUBJECT, resolverOf(CONTEXT));

        assertThat(first).as("пустое значение отдаётся как есть").isNull();
        assertThat(second).as("и приходит попаданием, а не новым резолвом").isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    private void givenTtl(Duration ttl) {
        properties.getMembership().setCacheTtl(ttl);
    }

    private Supplier<TenantContext> resolverOf(TenantContext context) {
        return () -> {
            calls.incrementAndGet();
            return context;
        };
    }

    private Supplier<TenantContext> failingResolver() {
        return () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("владелец членств не ответил");
        };
    }

    private Supplier<TenantContext> meetingResolver(CountDownLatch bothArrived, TenantContext context) {
        return () -> {
            calls.incrementAndGet();
            bothArrived.countDown();
            try {
                bothArrived.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return context;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entriesOf() {
        return (Map<String, Object>) ReflectionTestUtils.getField(cache, "entries");
    }

    /**
     * Дождаться, пока момент СТРОГО пройдёт: часов предмет операндом не
     * принимает.
     *
     * <p><b>Строго, а не «не раньше».</b> Срок записи — момент её постановки
     * плюс длительность, а вытеснение берёт только записи, чей срок строго
     * раньше текущего момента. Ожидание, вышедшее на самом моменте, на грубых
     * часах отдаёт вытеснению тот же момент — и запись на границе
     * остаётся: клетка краснела бы по разрешению часов, а не по предмету.
     */
    private void waitPast(Instant moment) {
        while (isFalse(Instant.now().isAfter(moment))) {
            Thread.onSpinWait();
        }
    }
}
