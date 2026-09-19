package com.example.bff.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.MembershipCache;
import com.example.bff.domain.TenantContext;
import com.example.bff.domain.TenantContextResolver;
import com.example.bff.integration.internal.api.AuthMembershipClient;
import com.example.bff.integration.internal.api.model.MembershipApiModel;
import com.example.platform.exception.PeerServiceUnavailableException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Вывод контекста тенанта из членств предъявителя — группа `U4` документа
 * `.claude/tests/cases/bff-perimeter-logic.md`
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p><b>Почему это стои́т проверять.</b> Контекст уезжает заголовком
 * владельцам, и они принимают его на веру: ошибка вывода не падает, а
 * ПОДМЕНЯЕТ тенанта — произвольно выбранное членство дало бы контекст, о
 * котором никто не решал, и отличить такой вызов от законного ниже по
 * тропе нечем.
 *
 * <p><b>Мок здесь ровно один, и он граница</b> — клиент членств. Кэш
 * настоящий: подменив его, кейс проверял бы вывод против сборки, которой
 * в проде нет (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 *
 * <p><b>Класс сменил прежнюю пробу того же предмета</b>
 * ({@code com.example.bff.TenantContextResolverTest}): её пять
 * утверждений — три ветви вывода, годная запись и перечитывание за сроком
 * — стоя́т здесь клетками `U4.1`, `U4.2`, `U4.4`, `U4.8` и `U3.3`
 * соседнего класса, и второй записи тех же ожиданий не заводится.
 */
class TenantContextResolverTest {

    private static final String SUBJECT = "user-42";
    private static final String BEARER = "Bearer token";
    private static final String OTHER_BEARER = "Bearer other-token";

    /** Ровно одно членство — оно и есть контекст. */
    @Test
    @DisplayName("U4.1 — единственное членство становится контекстом")
    void u4_1_aSingleMembershipBecomesTheContext() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"));

        TenantContext context = resolverOver(client).resolve(SUBJECT, BEARER);

        assertThat(context.tenantId()).isEqualTo("tenant-7");
        assertThat(context.role()).isEqualTo("OWNER");
        assertThat(TenantContext.class.getRecordComponents())
                .as("идентичность самого членства в контекст не едет")
                .hasSize(2);
    }

    /**
     * Ноль членств: пустой ответ сюда не доходит — владелец заводит тенанта
     * той же точкой резолва. Ветвь остаётся охраной: контекст не
     * выдумывается ни из чего.
     */
    @Test
    @DisplayName("U4.2 — пустой перечень членств отвечает запретительным статусом")
    void u4_2_anEmptyMembershipListIsRefused() {
        AuthMembershipClient client = clientReturning();

        assertThatThrownBy(() -> resolverOver(client).resolve(SUBJECT, BEARER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Отсутствие перечня и пустой перечень неразличимы. */
    @Test
    @DisplayName("U4.3 — пустая ссылка вместо перечня ведёт к тому же исходу")
    void u4_3_anAbsentMembershipListEndsTheSameWay() {
        AuthMembershipClient client = mock(AuthMembershipClient.class);
        when(client.resolveSelf(anyString())).thenReturn(null);

        assertThatThrownBy(() -> resolverOver(client).resolve(SUBJECT, BEARER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Больше одного — отказ, а не молчаливый выбор. */
    @Test
    @DisplayName("U4.4 — два членства отвечают конфликтом, и ни одно наружу не уходит")
    void u4_4_twoMembershipsAnswerWithAConflict() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"),
                new MembershipApiModel("m-2", "tenant-8", "TRADER"));

        assertThatThrownBy(() -> resolverOver(client).resolve(SUBJECT, BEARER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /** Ветвь мерит «больше одного», а не «ровно два». */
    @Test
    @DisplayName("U4.5 — три членства ведут к тому же исходу, что и два")
    void u4_5_threeMembershipsEndTheSameWayAsTwo() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"),
                new MembershipApiModel("m-2", "tenant-8", "TRADER"),
                new MembershipApiModel("m-3", "tenant-9", "TRADER"));

        assertThatThrownBy(() -> resolverOver(client).resolve(SUBJECT, BEARER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * Сверки ответа соседа нет ни одной: пустой тенант проходит вывод
     * целиком (находка `F1`). Состояние недостижимо при исправном
     * владельце членств — его форма тенанта несёт.
     */
    @Test
    @DisplayName("U4.6 — членство с пустым тенантом отдаёт контекст с пустым тенантом")
    void u4_6_aMembershipWithoutATenantYieldsAnEmptyTenantContext() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", null, "OWNER"));

        TenantContext context = resolverOver(client).resolve(SUBJECT, BEARER);

        assertThat(context.tenantId()).as("пустота проходит дальше по тропе").isNull();
    }

    /** Роль едет с первого дня и вывода не задевает ни в одной ветви. */
    @Test
    @DisplayName("U4.7 — членство с пустой ролью отдаёт контекст с пустой ролью")
    void u4_7_aMembershipWithoutARoleYieldsAnEmptyRole() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", null));

        TenantContext context = resolverOver(client).resolve(SUBJECT, BEARER);

        assertThat(context.tenantId()).isEqualTo("tenant-7");
        assertThat(context.role()).as("от роли поведение вывода не зависит").isNull();
    }

    /** Годная запись владельца не переспрашивает. */
    @Test
    @DisplayName("U4.8 — второй вызов в пределах срока годности к владельцу не идёт")
    void u4_8_aSecondCallWithinTheTtlDoesNotReachTheOwner() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"));
        TenantContextResolver resolver = resolverOver(client);

        TenantContext first = resolver.resolve(SUBJECT, BEARER);
        TenantContext second = resolver.resolve(SUBJECT, BEARER);

        verify(client, times(1)).resolveSelf(anyString());
        assertThat(second).as("отдаётся тот же контекст").isSameAs(first);
    }

    /** Отказ соседа по ярусу проходит наружу своим классом и не кэшируется. */
    @Test
    @DisplayName("U4.9 — отказ недоступности соседа проходит наружу и записи не заводит")
    void u4_9_aPeerFailurePassesThroughAndIsNotCached() {
        AuthMembershipClient client = mock(AuthMembershipClient.class);
        when(client.resolveSelf(anyString()))
                .thenThrow(new PeerServiceUnavailableException("владелец членств не ответил", null));
        TenantContextResolver resolver = resolverOver(client);

        assertThatThrownBy(() -> resolver.resolve(SUBJECT, BEARER))
                .as("класс отказа соседа по ярусу свой")
                .isInstanceOf(PeerServiceUnavailableException.class);
        assertThatThrownBy(() -> resolver.resolve(SUBJECT, BEARER))
                .isInstanceOf(PeerServiceUnavailableException.class);

        verify(client, times(2)).resolveSelf(anyString());
    }

    /** Ключ кэша — субъект, а не токен: второй токен в тропу не попадает. */
    @Test
    @DisplayName("U4.10 — тот же субъект с другим токеном получает сохранённый контекст")
    void u4_10_theSameSubjectWithAnotherTokenGetsTheStoredContext() {
        AuthMembershipClient client = clientReturning(new MembershipApiModel("m-1", "tenant-7", "OWNER"));
        TenantContextResolver resolver = resolverOver(client);

        TenantContext first = resolver.resolve(SUBJECT, BEARER);
        TenantContext second = resolver.resolve(SUBJECT, OTHER_BEARER);

        assertThat(second).as("отдаётся сохранённый контекст").isSameAs(first);
        verify(client, times(1)).resolveSelf(BEARER);
        verify(client, times(0)).resolveSelf(OTHER_BEARER);
    }

    private AuthMembershipClient clientReturning(MembershipApiModel... memberships) {
        AuthMembershipClient client = mock(AuthMembershipClient.class);
        when(client.resolveSelf(anyString())).thenReturn(List.of(memberships));
        return client;
    }

    private TenantContextResolver resolverOver(AuthMembershipClient client) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.getMembership().setCacheTtl(Duration.ofMinutes(10));
        return new TenantContextResolver(client, new MembershipCache(properties));
    }
}
