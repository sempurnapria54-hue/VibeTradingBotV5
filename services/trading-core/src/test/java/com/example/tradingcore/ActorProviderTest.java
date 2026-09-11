package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingcore.config.AsyncActorContextConfigurer;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.util.Constants;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Кто инициировал ход и по какой тропе ответ доходит до записи
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 *
 * <p><b>Почему это стои́т проверять.</b> Ошибка резолвера тихая и
 * правдоподобная: подъём ступени, запрошенный держателем, получил бы класс
 * контура вместо его имени — и журнал ответил бы на «кто остановил контур»
 * неверно, а не пустотой. Отличить такую строку постфактум нечем
 * (docs/concept.md П3), а самое дорогое действие контура совершается
 * СНАРУЖИ (docs/models/domain/other/Auditable.md).
 *
 * <p><b>Отдельно проверяется ПЕРЕНОС контекста в чужой тред, и у ядра он
 * не гипотетический:</b> полную ручную остановку держатель запускает через
 * асинхронный фасад, а её ход пишет и строку отчёта, и событие подъёма
 * ступени. Наследуемый тред-локал здесь не годится по построению — он
 * отдаёт контекст в момент создания треда, а треды пула переиспользуются.
 *
 * <p><b>Чего тест НЕ мерит, и это названо.</b> Он не поднимает контекст
 * приложения: {@code @SpringBootTest} у модуля нет — он потянул бы БД и
 * соседа. Поэтому мерится поведение самого поставщика и обёртки
 * исполнителя, а не то, что каркас спросит наш {@code AsyncConfigurer};
 * расхождение там пришло бы громко — отказом подъёма либо неработающим
 * ручным триггером, а не молчанием.
 */
class ActorProviderTest {

    private static final String PRESENTED_PRINCIPAL = "holder";

    private final ActorProvider actorProvider = new ActorProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Ход без внешнего инициатора: расписание, реакция обработчика. */
    @Test
    @DisplayName("Пустой контекст даёт класс контура, а не пустоту и не имя")
    void anEmptyContextYieldsTheContourClass() {
        assertThat(actorProvider.currentActor())
                .as("пусто в контексте означает «внешнего инициатора нет» — это признак, а не умолчание")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /** Открытые точки контура отдают анонима; удостоверённым он не является. */
    @Test
    @DisplayName("Анонимная аутентификация актором не становится")
    void anAnonymousTokenIsNotAnActor() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "probe", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(actorProvider.currentActor())
                .as("имя анонима утверждало бы, что ход начал субъект, которого контур не удостоверил")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /** Заявленное, но не принятое имя — то же, что и непредъявленное. */
    @Test
    @DisplayName("Непринятый принципал актором не становится")
    void anUnauthenticatedTokenIsNotAnActor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("claimed-name", "wrong-secret"));

        assertThat(actorProvider.currentActor())
                .as("заявленное, но не удостоверенное имя было бы записью непроверенного как факта")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /** Ход, порождённый внешним вызовом под предъявленным принципалом. */
    @Test
    @DisplayName("Предъявленный принципал становится актором хода")
    void aPresentedPrincipalBecomesTheActor() {
        givenPresentedPrincipal();

        assertThat(actorProvider.currentActor())
                .as("ход порождён внешним вызовом, и актор — его принципал")
                .isEqualTo(PRESENTED_PRINCIPAL);
    }

    /**
     * Контекст хода переживает смену треда.
     *
     * <p>Это и есть предмет обёртки исполнителя: без неё запись,
     * созданная на асинхронной тропе, получила бы класс контура при живом
     * принципале.
     */
    @Test
    @DisplayName("Актор доходит до задачи, исполняемой в чужом треде")
    void theActorSurvivesTheAsyncFacadeThread() throws Exception {
        givenPresentedPrincipal();

        assertThat(actorSeenBy(AsyncActorContextConfigurer.propagating(new SimpleAsyncTaskExecutor())))
                .as("перенос контекста в порождённый тред — часть тропы, а не деталь реализации")
                .isEqualTo(PRESENTED_PRINCIPAL);
    }

    /**
     * Контроль, ради которого обёртка и заведена: голый исполнитель
     * контекста не переносит, и актор на чужом треде вырождается в класс
     * контура.
     */
    @Test
    @DisplayName("Без обёртки актор в чужом треде теряется — это и есть предмет обёртки")
    void withoutTheWrapperTheActorIsLostInTheForeignThread() throws Exception {
        givenPresentedPrincipal();

        assertThat(actorSeenBy(new SimpleAsyncTaskExecutor()))
                .as("голый исполнитель отдал бы правдоподобное и неверное значение")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /** Актор, увиденный задачей, которую исполнил переданный исполнитель. */
    private String actorSeenBy(AsyncTaskExecutor executor) throws Exception {
        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(actorProvider.currentActor()));
        return seen.get(5, TimeUnit.SECONDS);
    }

    private void givenPresentedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                PRESENTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
    }
}
