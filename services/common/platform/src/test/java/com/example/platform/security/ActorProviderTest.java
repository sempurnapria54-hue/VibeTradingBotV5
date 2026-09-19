package com.example.platform.security;

import static com.example.platform.util.Constants.Audit.SYSTEM_PRINCIPAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Кто инициировал ход: группа `U3` и клетка `U14.6` документа
 * `.claude/tests/cases/platform-shared-logic.md`
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 *
 * <p><b>Почему это стои́т проверять.</b> Ошибка резолвера тихая и
 * правдоподобная: ход, запрошенный человеком, получил бы класс контура
 * вместо его имени — и журнал ответил бы на «кто это сделал» неверно, а не
 * пустотой. Отличить такую строку постфактум нечем (docs/concept.md П3).
 *
 * <p><b>Проба живёт у формы, а не у потребителя.</b> Прежде ветви актора
 * стоя́ли копиями в тестовых деревьях {@code trading-core} и
 * {@code strategies}, хотя правится форма здесь (§«Решение: где живёт
 * дерево прогона»). Здесь остались ровно ветви признака; ПЕРЕНОС контекста
 * в чужой тред остался в деревьях сервисов — там лежит его класс
 * ({@code AsyncActorContextConfigurer}), и на одном classpath с этим тестом
 * его нет.
 *
 * <p><b>Контекст ставится явно и снимается после КАЖДОГО кейса:</b>
 * {@code SecurityContextHolder} — тред-локал, и незачищенный контекст утёк
 * бы в соседний кейс дерева, сделав зелёным то, что должно краснеть.
 *
 * <p><b>Чего тест НЕ мерит, и это названо.</b> Он не поднимает контекст
 * приложения: у артефакта его нет вовсе. Мерится поведение самого
 * поставщика, а не то, что каркас спросит его у сервиса; расхождение там
 * пришло бы громко — неверным актором в строке, а не молчанием.
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
    @DisplayName("U3.1 — пустой контекст даёт класс контура, а не пустоту и не имя")
    void u3_1_anEmptyContextYieldsTheContourClass() {
        assertThat(actorProvider.currentActor())
                .as("пусто в контексте означает «внешнего инициатора нет» — это признак, а не умолчание")
                .isEqualTo(SYSTEM_PRINCIPAL);
    }

    /** Открытые точки контура отдают анонима; удостоверённым он не является. */
    @Test
    @DisplayName("U3.2 — анонимная аутентификация актором не становится")
    void u3_2_anAnonymousTokenIsNotAnActor() {
        givenContext(new AnonymousAuthenticationToken(
                "probe", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(actorProvider.currentActor())
                .as("имя анонима утверждало бы, что ход начал субъект, которого контур не удостоверил")
                .isEqualTo(SYSTEM_PRINCIPAL);
    }

    /** Заявленное, но не принятое имя — то же, что и непредъявленное. */
    @Test
    @DisplayName("U3.3 — непринятый принципал актором не становится")
    void u3_3_anUnauthenticatedTokenIsNotAnActor() {
        givenContext(new UsernamePasswordAuthenticationToken("claimed-name", "wrong-secret"));

        assertThat(actorProvider.currentActor())
                .as("непринятые креды — тот же класс, что и непредъявленные: записать заявленное "
                        + "имя значило бы записать непроверенное как факт")
                .isEqualTo(SYSTEM_PRINCIPAL);
    }

    /** Ход, порождённый внешним вызовом под предъявленным принципалом. */
    @Test
    @DisplayName("U3.4 — предъявленный принципал становится актором хода")
    void u3_4_aPresentedPrincipalBecomesTheActor() {
        givenContext(presentedPrincipal());

        assertThat(actorProvider.currentActor())
                .as("ход порождён внешним вызовом, и актор — его принципал")
                .isEqualTo(PRESENTED_PRINCIPAL);
    }

    @Test
    @DisplayName("U3.5 — принятый токен с пустым принципалом уходит ПУСТОЙ СТРОКОЙ, а не классом контура")
    void u3_5_anAcceptedTokenWithoutAPrincipalYieldsAnEmptyName() {
        givenContext(new UsernamePasswordAuthenticationToken(
                null, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(actorProvider.currentActor())
                .as("третьего класса значений у актора нет: пустая строка ушла бы ИМЕНЕМ. "
                        + "Состояние недостижимо — контур принимает только JwtAuthenticationToken, "
                        + "— и направление ошибки разрешающее")
                .isEmpty();
    }

    @Test
    @DisplayName("U3.6 — у принятого токена контура берётся имя принципала, а не сам токен")
    void u3_6_theJwtPrincipalNameIsTakenRatherThanTheToken() {
        Instant issued = Instant.now();
        givenContext(new JwtAuthenticationToken(Jwt.withTokenValue("opaque-token-value")
                .header("alg", "RS256")
                .subject(PRESENTED_PRINCIPAL)
                .claim("scope", "trading.read")
                .issuedAt(issued)
                .expiresAt(issued.plus(5, ChronoUnit.MINUTES))
                .build(), AuthorityUtils.createAuthorityList("SCOPE_trading.read")));

        assertThat(actorProvider.currentActor())
                .as("имя — claim субъекта; представление токена в строку не уходит")
                .isEqualTo(PRESENTED_PRINCIPAL);
    }

    @Test
    @DisplayName("U3.7 — резолв читает и не пишет: второй вызов подряд отдаёт то же")
    void u3_7_theResolutionIsIdempotentAndDoesNotTouchTheContext() {
        Authentication presented = presentedPrincipal();
        givenContext(presented);

        String first = actorProvider.currentActor();
        String second = actorProvider.currentActor();

        assertThat(first).isEqualTo(second).isEqualTo(PRESENTED_PRINCIPAL);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("контекст не изменён и не очищен")
                .isSameAs(presented);
    }

    // --- U14.6: чего резолвер не делает -----------------------------------

    @Test
    @DisplayName("U14.6 — резолвер не пишет в контекст безопасности и не очищает его")
    void u14_6_theResolverNeitherWritesNorClearsTheContext() {
        actorProvider.currentActor();

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("на пустом контексте значение берётся по признаку, а не проставляется в контекст")
                .isNull();

        Authentication presented = presentedPrincipal();
        givenContext(presented);
        actorProvider.currentActor();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(presented);
    }

    private static void givenContext(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Authentication presentedPrincipal() {
        return new UsernamePasswordAuthenticationToken(
                PRESENTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
