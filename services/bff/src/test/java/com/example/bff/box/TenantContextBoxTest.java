package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.apache.commons.lang3.BooleanUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B1} документа кейсов: контекст тенанта выводится из
 * членств предъявителя (.claude/tests/cases/bff.md §«B1 — Контекст
 * тенанта: вывод из членств предъявителя»).
 *
 * <p><b>Здесь клетки, которым довольно ШТАТНОГО положения осей.</b> Срок
 * годности записи кэша у них заведомо больше длительности клетки, и
 * этого требуют сами кейсы {@code B1.7} и {@code B1.10}; клетки, чей
 * предмет — ИСТЕЧЕНИЕ срока, живут своим классом
 * ({@link MembershipCacheExpiryBoxTest}) и платят своим контекстом.
 *
 * <p><b>«Подписка не открыта» читается ТИПОМ СОДЕРЖИМОГО ответа, и это
 * названо, а не умолчано.</b> Открытых подписок реплика наружу не
 * отдаёт ничем — поверхности у них нет, а базы у периметра нет вовсе
 * (.claude/tests/cases/bff.md §«Новая ось формы»). Наблюдаемое
 * различение одно: ответ провода идёт {@code text/event-stream} и не
 * заканчивается, а ответ контекста — законченный документ JSON. Клетка
 * утверждает именно это.
 */
class TenantContextBoxTest extends SharedBffBox {

    @Test
    @DisplayName("B1.1 — Ровно одно членство и есть контекст")
    void b1_1_oneMembershipIsTheContext() {
        authAnswersOneMembership();

        Answer answer = get(CONTEXT);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject())
                .containsEntry("tenantId", tenant)
                .containsEntry("role", ROLE)
                .containsOnlyKeys("tenantId", "role");
        LoggedRequest resolve = owners.single(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH);
        assertThat(resolve.getMethod().getName()).isEqualTo("POST");
        assertThat(owners.addresses()).containsExactly(OwnerStub.AUTH + " " + OwnerStub.MEMBERSHIPS_PATH);
        assertThat(recordsSinceStart()).isZero();
        assertThat(answer.header("Content-Type")).startsWith("application/json");
    }

    @Test
    @DisplayName("B1.2 — Резолв идёт под токеном ПОЛЬЗОВАТЕЛЯ, а не под служебной идентичностью")
    void b1_2_theResolutionGoesUnderTheUserToken() {
        authAnswersOneMembership();
        String presented = token();

        Answer answer = getWith(CONTEXT, presented);

        assertThat(answer.status()).isEqualTo(200);
        LoggedRequest resolve = owners.single(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH);
        assertThat(resolve.header("Authorization").values())
                .containsExactly("Bearer " + presented);
        assertThat(identity.paths()).doesNotContain(IdentityStub.tokenPath());
    }

    @Test
    @DisplayName("B1.3 — Больше одного членства — отказ, а не молчаливый выбор")
    void b1_3_moreThanOneMembershipIsRefused() {
        authAnswers(Bodies.membershipsOf(tenant, secondTenant));

        Answer answer = get(CONTEXT);

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(REQUEST_REJECTED);
        assertThat(answer.errorMessage()).contains("членств больше одного");
        // Момент отказа из тела вынимается: ISO-момент законно несёт «T1»
        // и «T2» часом суток, и отрицание по всему телу краснело бы по
        // часам прогона, а не по тенантам (ловушка TC-141).
        assertThat(answer.body().replace(String.valueOf(answer.asObject().get("occurredAt")), ""))
                .doesNotContain(tenant).doesNotContain(secondTenant);

        // Отказ не кэшируется: следующий запрос того же субъекта снова
        // зовёт владельца — иначе промах решался бы однажды и навсегда.
        Answer repeated = get(CONTEXT);

        assertThat(repeated.errorCode()).isEqualTo(REQUEST_REJECTED);
        assertThat(owners.requests(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH)).hasSize(2);
        assertThat(owners.addressedOwners()).containsOnly(OwnerStub.AUTH);
    }

    @Test
    @DisplayName("B1.4 — Пустой ответ владельца членств периметр тенанта не заводит")
    void b1_4_anEmptyMembershipAnswerCreatesNoTenant() {
        authAnswers(Bodies.noMemberships());

        Answer answer = get(CONTEXT);

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(REQUEST_REJECTED);
        assertThat(answer.errorMessage()).contains("членств у предъявителя нет");
        // Сам периметр тенанта не заводит: второго — создающего — вызова
        // к владельцу не уходит, и значения тенанта он не порождает.
        assertThat(owners.requests(OwnerStub.AUTH)).hasSize(1);
        assertThat(answer.body()).doesNotContain("tenantId");

        Answer repeated = get(CONTEXT);

        assertThat(repeated.errorCode()).isEqualTo(REQUEST_REJECTED);
        assertThat(owners.requests(OwnerStub.AUTH)).hasSize(2);
    }

    @Test
    @DisplayName("B1.5 — Отказ транспорта у владельца членств — свой класс, и резолв не повторяется")
    void b1_5_aTransportFailureOfTheMembershipOwnerIsItsOwnClass() {
        owners.failsTransport(OwnerStub.AUTH);

        Answer answer = get(CONTEXT);

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(PEER_UNAVAILABLE);
        // Глагол резолва мутирующий, и бюджету повторов чтения он не
        // подлежит: попытка ровно одна.
        assertThat(owners.requests(OwnerStub.AUTH)).hasSize(1);

        Answer repeated = get(CONTEXT);

        assertThat(repeated.errorCode()).isEqualTo(PEER_UNAVAILABLE);
        assertThat(owners.requests(OwnerStub.AUTH)).hasSize(2);
    }

    @Test
    @DisplayName("B1.6 — Присланный браузером тенант не читается ни в одном поле")
    void b1_6_aTenantSentByTheBrowserIsReadNowhere() {
        authAnswersOneMembership();
        String sent = "T9";
        String presented = token();

        Answer answer = getWith(CONTEXT + "?tenantId=" + sent, presented,
                Map.of(TENANT_HEADER, sent, ROLE_HEADER, ROLE));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject()).containsEntry("tenantId", tenant);
        assertThat(answer.body()).doesNotContain(sent);
        LoggedRequest resolve = owners.single(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH);
        assertThat(resolve.getUrl()).doesNotContain(sent);
        assertThat(resolve.getBodyAsString()).doesNotContain(sent);
        // Присланные заголовки контекста дальше не уезжают вовсе: их имён
        // в запросе к владельцу нет.
        assertThat(resolve.getAllHeaderKeys())
                .doesNotContain(TENANT_HEADER)
                .doesNotContain(ROLE_HEADER);
        // Заголовок предъявления сверяется РАВЕНСТВОМ, а не отсутствием
        // подстроки, и это не послабление: токен непрозрачен, его base64
        // законно несёт любые два знака — вхождение «T9» в него говорило
        // бы о кодировке, а не о том, что периметр прочёл присланное.
        assertThat(resolve.getHeader("Authorization")).isEqualTo("Bearer " + presented);
        assertThat(resolve.getAllHeaderKeys().stream()
                .filter(name -> BooleanUtils.isFalse("Authorization".equalsIgnoreCase(name)))
                .map(resolve::getHeader)
                .toList())
                .noneMatch(value -> value.contains(sent));
    }

    @Test
    @DisplayName("B1.7 — Годная запись кэша владельца не спрашивает")
    void b1_7_aFreshCacheEntryDoesNotAskTheOwner() {
        authAnswersOneMembership();

        Answer first = get(CONTEXT);
        Answer second = get(CONTEXT);

        assertThat(first.status()).isEqualTo(200);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(owners.requests(OwnerStub.AUTH, OwnerStub.MEMBERSHIPS_PATH)).hasSize(1);
    }

    @Test
    @DisplayName("B1.10 — Контекст ничего не пишет и ничего не публикует")
    void b1_10_theContextWritesNothingAndPublishesNothing() {
        authAnswersOneMembership();

        List<Integer> statuses = IntStream.range(0, 10)
                .mapToObj(attempt -> get(CONTEXT).status())
                .toList();

        assertThat(statuses).containsOnly(200);
        assertThat(recordsSinceStart()).isZero();
        assertThat(owners.addressedOwners()).containsOnly(OwnerStub.AUTH);
        // Единственный побочный след — запросы к владельцу членств числом
        // ПРОМАХОВ кэша: десять чтений дают один промах.
        assertThat(owners.requests(OwnerStub.AUTH)).hasSize(1);
    }
}
