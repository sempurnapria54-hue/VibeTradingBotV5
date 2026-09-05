package com.example.auth.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
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
 * <p><b>Автора записи здесь нет намеренно.</b> Строки реестра заводит
 * вызов человека, и подставлять фиксированное имя писателя значило бы
 * терять того, кто действительно завёл счёт; поле остаётся пустым, пока
 * тропа регистрации не назовёт принципала (`docs/rules/api-access-policy.md`).
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditConfig {

    /** Момент записи — всегда в UTC, как требует шкала времени системы. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
