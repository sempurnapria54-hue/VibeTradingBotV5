package com.example.auth.config;

import com.example.platform.security.ActorProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Включает JPA auditing: системные audit-поля строк проставляет
 * персистентность (.claude/rules/codestyle.md §«Auditable по слоям»).
 *
 * <p><b>Момент записи даёт свой поставщик.</b> Умолчание аудита отдаёт
 * {@code LocalDateTime}, а audit-поля объявлены {@code OffsetDateTime}
 * (шкала одна — UTC, docs/rules/time-utc.md): без своего поставщика КАЖДАЯ
 * запись падает на «Cannot convert unsupported date type». Обнаружено
 * первым живым прогоном на стенде у соседнего сервиса — форма у обоих
 * одна.
 *
 * <p><b>Резолвер актора появился вместе с общим носителем предиката.</b>
 * Прежде его здесь не было, и {@code created_by} оставался пустым <b>за
 * отсутствием резолвера</b>, а не по признаку — то есть пустота не
 * означала «внешнего инициатора нет», она не означала ничего
 * (docs/rules/absent-value-semantics.md). Заводить его четвёртой копией
 * предиката было дороже, чем оставить долг; с переездом предиката в общий
 * артефакт цена упала до одной строки, и долг закрыт.
 *
 * <p><b>Значение отдаёт общий поставщик</b> ({@link ActorProvider}), а не
 * приватный метод конфигурации: классы значений, их признак и обе тропы
 * отказа доступа названы у него, и второй их носитель разошёлся бы с
 * первым первой же правкой (.claude/rules/policy-home.md). Пока принципал
 * у поверхности один, актором строк реестра идёт класс контура; при
 * втором субъекте та же тропа отдаст его имя, не меняя ни строки здесь
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditConfig {

    /** Момент записи — всегда в UTC, как требует шкала времени системы. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Резолвер актора записи: «кто инициировал ход». */
    @Bean
    public AuditorAware<String> auditorAware(ActorProvider actorProvider) {
        return () -> Optional.of(actorProvider.currentActor());
    }
}
