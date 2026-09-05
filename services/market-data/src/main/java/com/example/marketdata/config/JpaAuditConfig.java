package com.example.marketdata.config;

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
 * <p><b>Автор записи — сам сервис, а не человек.</b> Ряды рынка пишет
 * джоба, у которой пользователя нет по построению; подставлять сюда
 * принципала входящего вызова значило бы приписывать читателю авторство
 * того, что записал сбор.
 *
 * <p><b>Момент записи даёт свой поставщик, и это не украшение.</b>
 * Умолчание аудита отдаёт {@code LocalDateTime}, а audit-поля объявлены
 * {@code OffsetDateTime} (шкала одна — UTC, docs/rules/time-utc.md): без
 * своего поставщика КАЖДАЯ запись падает на «Cannot convert unsupported
 * date type». Обнаружено первым живым прогоном на стенде — синк листинга
 * не сохранил ни одной строки.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditConfig {

    /** Имя писателя строк рыночных данных. */
    private static final String WRITER = "market-data";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(WRITER);
    }

    /** Момент записи — всегда в UTC, как требует шкала времени системы. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
