package com.example.testsupport;

import static com.example.platform.util.Constants.Audit.SYSTEM_PRINCIPAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.security.ActorProvider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * Перенос контекста хода в чужой тред: группа `U11` и клетка `U12.3`
 * документа `.claude/tests/cases/platform-shared-logic.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии.</b> {@code AsyncActorContextConfigurer} живёт двумя экземплярами —
 * {@code trading-core} и {@code strategies}, — и различий у них нет ни
 * одного (`U12.3`); сличить их на одном classpath нечем.
 *
 * <p><b>Базовых сборок ДВЕ, и вторая — не деталь входа, а другой
 * исполнитель.</b> У {@code SimpleAsyncTaskExecutor} пула нет по
 * построению — он поднимает новый тред на каждую задачу, и «тот же тред» на
 * нём не строится ничем. Остаток контекста наблюдается только сборкой B —
 * пулом в один тред.
 *
 * <p><b>Точка наблюдения остатка — ГОЛЫЙ пул, а не обёрнутый.</b> Обёртка
 * снимает контекст вызывающего в момент ПОСТАНОВКИ задачи: задача,
 * поданная обёрнутым исполнителем при пустом контексте вызывающего, увидит
 * пусто независимо от того, чистит ли обёртка за предыдущей, — то есть
 * кейс был бы зелен при любом поведении предмета. Живость самой точки
 * наблюдения держит `U11.6`.
 */
public abstract class ActorContextPropagationContract {

    protected static final String PRESENTED_PRINCIPAL = "holder";

    private final ActorProvider actorProvider = new ActorProvider();

    /** Порт к своей копии: исполнитель, переносящий контекст хода. */
    protected abstract AsyncTaskExecutor propagating(AsyncTaskExecutor delegate);

    /** Порт к своей копии: исполнитель, который каркас спросит у конфигуратора. */
    protected abstract Executor asyncExecutorWith(AsyncTaskExecutor applicationExecutor);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- сборка A: тред на задачу -----------------------------------------

    @Test
    @DisplayName("U11.1 — актор доходит до задачи, исполняемой в чужом треде")
    void u11_1_theActorSurvivesTheThreadChange() throws Exception {
        givenPresentedPrincipal();

        assertThat(actorSeenBy(propagating(new SimpleAsyncTaskExecutor())))
                .as("перенос контекста в порождённый тред — часть тропы, а не деталь реализации")
                .isEqualTo(PRESENTED_PRINCIPAL);
    }

    @Test
    @DisplayName("U11.2 — без обёртки актор в чужом треде теряется: это и есть предмет обёртки")
    void u11_2_withoutTheWrapperTheActorIsLost() throws Exception {
        givenPresentedPrincipal();

        assertThat(actorSeenBy(new SimpleAsyncTaskExecutor()))
                .as("ошибка при этом тихая: значение правдоподобно и неверно")
                .isEqualTo(SYSTEM_PRINCIPAL);
    }

    // --- сборка B: пул в один тред ----------------------------------------

    @Test
    @DisplayName("U11.3 — переиспользованный тред чужого принципала не наследует")
    void u11_3_aReusedPoolThreadDoesNotInheritTheForeignPrincipal() throws Exception {
        ThreadPoolTaskExecutor pool = singleThreadPool();
        try {
            givenPresentedPrincipal();
            String workerThread = threadOfCleanTaskVia(propagating(pool));
            SecurityContextHolder.clearContext();

            Seen leftover = seenByBareTask(pool);

            assertThat(leftover.thread())
                    .as("наблюдение имеет смысл только на ТОМ ЖЕ треде")
                    .isEqualTo(workerThread);
            assertThat(leftover.actor())
                    .as("обёртка очистила контекст после исполнения")
                    .isEqualTo(SYSTEM_PRINCIPAL);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("U11.4 — делегатом становится исполнитель приложения, а не свой пул")
    void u11_4_theDelegateIsTheApplicationExecutor() throws Exception {
        AtomicReference<String> ranOn = new AtomicReference<>();
        AsyncTaskExecutor application = new SimpleAsyncTaskExecutor("application-");
        Executor configured = asyncExecutorWith(new AsyncTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                ranOn.set("application");
                application.execute(task);
            }
        });

        CompletableFuture<String> done = new CompletableFuture<>();
        configured.execute(() -> done.complete("ok"));
        done.get(5, TimeUnit.SECONDS);

        assertThat(ranOn.get())
                .as("собственный пул стал бы вторым носителем настроек исполнения")
                .isEqualTo("application");
    }

    @Test
    @DisplayName("U11.5 — отказ задачи приходит вызывающему нетронутым, а контекст всё равно очищен")
    void u11_5_aFailingTaskStillClearsTheContext() throws Exception {
        ThreadPoolTaskExecutor pool = singleThreadPool();
        try {
            givenPresentedPrincipal();
            Runnable boom = () -> {
                throw new IllegalStateException("boom");
            };
            Future<?> failed = propagating(pool).submit(boom);

            assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS))
                    .as("обёртка исключения не подменяет и не глотает")
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom");

            SecurityContextHolder.clearContext();
            assertThat(seenByBareTask(pool).actor())
                    .as("контекст очищен finally, а не только на чистом конце")
                    .isEqualTo(SYSTEM_PRINCIPAL);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("U11.6 — контроль живости: неснятый контекст тред переживает")
    void u11_6_anUnclearedContextOutlivesTheTask() throws Exception {
        ThreadPoolTaskExecutor pool = singleThreadPool();
        try {
            CompletableFuture<String> planted = new CompletableFuture<>();
            pool.execute(() -> {
                SecurityContextHolder.getContext().setAuthentication(presentedPrincipal());
                planted.complete(Thread.currentThread().getName());
            });
            String workerThread = planted.get(5, TimeUnit.SECONDS);

            Seen leftover = seenByBareTask(pool);

            assertThat(leftover.thread()).isEqualTo(workerThread);
            assertThat(leftover.actor())
                    .as("без этого кейса «пусто» соседей было бы неотличимо от «контекста тут не бывает»")
                    .isEqualTo(PRESENTED_PRINCIPAL);
        } finally {
            pool.shutdown();
        }
    }

    // --- U12.3: тождество копий -------------------------------------------

    @Test
    @DisplayName("U12.3 — различий у копий переносчика нет ни одного")
    void u12_3_theCopiesOfTheConfigurerDoNotDiffer() {
        assertThat(propagating(new SimpleAsyncTaskExecutor()))
                .as("обе копии отдают одну и ту же обёртку каркаса")
                .isInstanceOf(DelegatingSecurityContextAsyncTaskExecutor.class);
    }

    // --- оснастка ---------------------------------------------------------

    /** Что увидела задача и на каком треде она это увидела. */
    protected record Seen(String actor, String thread) {
    }

    private String actorSeenBy(AsyncTaskExecutor executor) throws Exception {
        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(actorProvider.currentActor()));
        return seen.get(5, TimeUnit.SECONDS);
    }

    /** Чистый проход обёрнутым исполнителем; возвращает тред, на котором он шёл. */
    private String threadOfCleanTaskVia(AsyncTaskExecutor executor) throws Exception {
        CompletableFuture<String> done = new CompletableFuture<>();
        executor.execute(() -> done.complete(Thread.currentThread().getName()));
        return done.get(5, TimeUnit.SECONDS);
    }

    /** Задача, поданная ГОЛЫМ пулом: она видит ровно то, что осталось на треде. */
    private Seen seenByBareTask(ThreadPoolTaskExecutor pool) throws Exception {
        CompletableFuture<Seen> seen = new CompletableFuture<>();
        pool.execute(() -> seen.complete(
                new Seen(actorProvider.currentActor(), Thread.currentThread().getName())));
        return seen.get(5, TimeUnit.SECONDS);
    }

    private static ThreadPoolTaskExecutor singleThreadPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setThreadNamePrefix("single-");
        pool.initialize();
        return pool;
    }

    private void givenPresentedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(presentedPrincipal());
    }

    private static UsernamePasswordAuthenticationToken presentedPrincipal() {
        return new UsernamePasswordAuthenticationToken(
                PRESENTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
