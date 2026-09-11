package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.auditstatistics.config.JpaAuditConfig;
import com.example.auditstatistics.domain.service.ActorProvider;
import com.example.auditstatistics.persistence.model.journal.AccessDenialEntity;
import com.example.auditstatistics.persistence.model.journal.AuditableEntity;
import com.example.auditstatistics.util.Constants;
import jakarta.persistence.EntityListeners;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Актор строки и тропа, по которой он до неё доходит
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 *
 * <p><b>Почему это стои́т проверять.</b> Ошибка резолвера тихая и
 * правдоподобная: строка отказа получила бы имя субъекта, которого контур
 * <b>не удостоверил</b>, — то есть утверждала бы, что запись создал тот,
 * кого отвергли. Отличить такую строку постфактум нечем, а разбор упирался
 * бы в лог, который носителем наблюдаемости не является (docs/concept.md
 * П3).
 *
 * <p><b>Чего тест НЕ мерит, и это названо.</b> Он не поднимает контекст с
 * тремя фабриками сущностей: {@code @SpringBootTest} у модуля нет
 * намеренно — он потянул бы БД и брокер. Поэтому проверяется <b>объявленная
 * тропа</b> (аудит включён, имена бинов названы, слушатель унаследован
 * сущностью) и <b>поведение самого резолвера</b>, а не то, что контейнер
 * позовёт его в момент вставки: расхождение там пришло бы громко — отказом
 * подъёма, а не молчанием.
 */
class AccessDenialActorTest {

    private static final String ACCEPTED_PRINCIPAL = "holder";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Тропа непредъявленного принципала: внешнего инициатора, которого
     * контур удостоверил, нет — актором идёт класс контура.
     */
    @Test
    @DisplayName("Пустой контекст даёт класс контура, а не пустоту и не имя")
    void anEmptyContextYieldsTheContourClass() {
        assertThat(resolvedActor())
                .as("пусто в контексте означает «внешнего инициатора нет» — это признак, а не умолчание")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /**
     * Анонимная аутентификация значением не является: контур отдаёт её на
     * открытых точках, а неудостоверённое присутствие актором не
     * становится.
     */
    @Test
    @DisplayName("Анонимная аутентификация актором не становится")
    void anAnonymousTokenIsNotAnActor() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "probe", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(resolvedActor())
                .as("имя анонима утверждало бы, что запись создал субъект, которого контур не удостоверил")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /**
     * Тропа принятого принципала — вторая из двух, на которых отказ
     * доступа достижим: операция не разрешена тому, чью личность контур
     * удостоверил.
     */
    @Test
    @DisplayName("Принятый принципал становится актором записи")
    void anAcceptedPrincipalBecomesTheActor() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ACCEPTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(resolvedActor())
                .as("на тропе запрета операции ход порождён внешним вызовом, и актор — его принципал")
                .isEqualTo(ACCEPTED_PRINCIPAL);
    }

    /**
     * Непринятые креды дают ту же ветвь, что и непредъявленные: субъект
     * неизвестен одинаково, и различать здесь нечего.
     */
    @Test
    @DisplayName("Непринятый принципал актором не становится")
    void anUnauthenticatedTokenIsNotAnActor() {
        UsernamePasswordAuthenticationToken unaccepted =
                new UsernamePasswordAuthenticationToken("claimed-name", "wrong-secret");
        SecurityContextHolder.getContext().setAuthentication(unaccepted);

        assertThat(resolvedActor())
                .as("заявленное, но не удостоверенное имя — запись непроверенного как факта")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /**
     * Момент записи — UTC.
     *
     * <p>Умолчание аудита отдаёт {@code LocalDateTime}, а audit-поля
     * объявлены {@code OffsetDateTime}: без своего поставщика падала бы
     * КАЖДАЯ запись.
     */
    @Test
    @DisplayName("Момент записи приходит в UTC, а не в локальной шкале")
    void theRecordingMomentArrivesInUtc() {
        Optional<OffsetDateTime> moment = new JpaAuditConfig().auditingDateTimeProvider().getNow()
                .map(OffsetDateTime::from);

        assertThat(moment).isPresent();
        assertThat(moment.orElseThrow().getOffset())
                .as("шкала времени системы одна — UTC (docs/rules/time-utc.md)")
                .isEqualTo(ZoneOffset.UTC);
        assertThat(moment.orElseThrow())
                .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
    }

    /**
     * Тропа объявлена целиком: без имён бинов в аннотации контейнер взял
     * бы умолчания — и локальную шкалу времени, и отсутствующего актора.
     *
     * <p><b>Сверяется имя С БИНОМ, а не с литералом.</b> Ссылка,
     * разошедшаяся с именем метода, роняет подъём процесса — то есть
     * обнаруживается у того, кто разворачивает, а не у того, кто правил;
     * сравнение двух литералов такого расхождения не видит вовсе.
     */
    @Test
    @DisplayName("Аудит включён, и обе ссылки разрешаются в объявленные бины")
    void theAuditingWiringResolvesBothProviders() {
        EnableJpaAuditing enabled = JpaAuditConfig.class.getAnnotation(EnableJpaAuditing.class);

        assertThat(enabled).as("без включённого аудита поля остались бы пустыми молча").isNotNull();
        assertThat(beanMethod(enabled.auditorAwareRef()).getReturnType())
                .as("ссылка на резолвера актора обязана вести в объявленный бин")
                .isEqualTo(AuditorAware.class);
        assertThat(beanMethod(enabled.dateTimeProviderRef()).getReturnType())
                .as("ссылка на поставщика момента обязана вести в объявленный бин")
                .isEqualTo(DateTimeProvider.class);
    }

    private Method beanMethod(String name) {
        return Arrays.stream(JpaAuditConfig.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> Objects.equals(method.getName(), name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("бина с именем нет: " + name));
    }

    /**
     * Слушатель аудита достаётся строке наследованием, а поля — своими
     * аннотациями. Без слушателя аудит включён, а строке не достаётся
     * ничего: она вставляется с пустыми колонками и об этом не сообщает.
     */
    @Test
    @DisplayName("Строка отказа получает слушателя аудита и все четыре системных поля")
    void theDenialRowInheritsTheAuditingListenerAndItsFields() {
        assertThat(AccessDenialEntity.class.getSuperclass()).isEqualTo(AuditableEntity.class);

        EntityListeners listeners = AuditableEntity.class.getAnnotation(EntityListeners.class);
        assertThat(listeners).isNotNull();
        assertThat(listeners.value()).contains(AuditingEntityListener.class);

        List<Class<? extends Annotation>> systemFieldMarkers = List.of(
                CreatedDate.class, CreatedBy.class, LastModifiedDate.class, LastModifiedBy.class);
        systemFieldMarkers.forEach(marker -> assertThat(Arrays.stream(AuditableEntity.class.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(marker)))
                .as("системное поле %s обязано быть помечено — иначе оно останется пустым молча",
                        marker.getSimpleName())
                .isTrue());
    }

    private String resolvedActor() {
        return new JpaAuditConfig().auditorAware(new ActorProvider()).getCurrentAuditor().orElse(null);
    }
}
