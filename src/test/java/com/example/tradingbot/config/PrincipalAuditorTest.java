package com.example.tradingbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.util.Constants;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Executor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Актор записи: кто именно попадёт в {@code createdBy} / {@code modifiedBy}
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 *
 * <p>Проверяется <b>дискриминатор</b>: разделяет предъявленный принципал, а не
 * тред записи и не место исполнения. Последнее — отдельной пробой на пуле:
 * ошибка «по треду» тихая, значение правдоподобно и неверно, поэтому без
 * пробы она не видна.
 */
class PrincipalAuditorTest {

    private static final String HOLDER = "holder";

    private final AuditorAware<String> auditor = new JpaAuditConfig().auditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Внешний вызов под предъявленным принципалом — имя принципала")
    void externalCallUnderPrincipalWritesPrincipalName() {
        authenticateAs(HOLDER);

        assertThat(auditor.getCurrentAuditor()).contains(HOLDER);
    }

    @Test
    @DisplayName("Внешнего инициатора нет — класс контура, и это значение по признаку")
    void moveWithoutExternalInitiatorWritesContour() {
        // Пустой контекст: запланированный тик, реакция обработчика, восстановление.
        assertThat(auditor.getCurrentAuditor()).contains(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /**
     * Анонимная аутентификация — не субъект. Контур доступа отдаёт её на
     * открытой точке (проба живости); записать её именем актора значило бы
     * утверждать, что запись создал тот, кого контур не удостоверил.
     */
    @Test
    @DisplayName("Анонимная аутентификация актором не становится")
    void anonymousAuthenticationIsNotAnActor() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "probe", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(auditor.getCurrentAuditor()).contains(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /**
     * Несущая проба: ручной триггер джобы порождён человеком, а запись
     * создаётся в <b>чужом треде пула</b>. Без переноса контекста запись
     * получила бы класс контура — правдоподобно и неверно.
     */
    @Test
    @DisplayName("Контекст хода переживает смену треда на пуле асинхронного фасада")
    void moveContextSurvivesThreadHandoff() throws Exception {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.initialize();
        try {
            // Тред пула прогревается ЧУЖИМ ходом: наследование thread-local на
            // переиспользуемом треде не сработало бы, и проба это ловит.
            warmUp(pool);
            authenticateAs(HOLDER);
            Executor propagating = new AsyncSecurityContextConfig(pool).getAsyncExecutor();

            AtomicReference<String> seenInPool = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            propagating.execute(() -> {
                seenInPool.set(auditor.getCurrentAuditor().orElse(null));
                done.countDown();
            });

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(seenInPool.get())
                    .as("инициатор обязан доехать до записи вместе с ходом, а не подразумеваться по треду")
                    .isEqualTo(HOLDER);

            // КОНТРОЛЬ: тот же пул БЕЗ обёртки принципала не видит — значит
            // переносит именно она, а не пул сам по себе. Без контроля проба
            // прошла бы и на случайно унаследованном контексте.
            AtomicReference<String> seenUnwrapped = new AtomicReference<>();
            CountDownLatch controlDone = new CountDownLatch(1);
            pool.execute(() -> {
                seenUnwrapped.set(auditor.getCurrentAuditor().orElse(null));
                controlDone.countDown();
            });
            assertThat(controlDone.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(seenUnwrapped.get())
                    .as("голый пул контекста хода не переносит — иначе проба выше ничего не доказывает")
                    .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
        } finally {
            pool.shutdown();
        }
    }

    private void warmUp(ThreadPoolTaskExecutor pool) throws Exception {
        CountDownLatch warm = new CountDownLatch(1);
        pool.execute(warm::countDown);
        assertThat(warm.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private void authenticateAs(String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                name, "n/a", List.of(new SimpleGrantedAuthority("PRINCIPAL"))));
    }
}
