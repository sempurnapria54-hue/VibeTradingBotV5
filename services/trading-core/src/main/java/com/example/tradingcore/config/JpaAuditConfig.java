package com.example.tradingcore.config;

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
 * <p><b>Автор записи — сам сервис.</b> Торговые строки пишут проходы
 * оркестрации и исполнители, у которых пользователя нет по построению;
 * подставлять принципала входящего вызова значило бы приписывать
 * читателю авторство того, что записала машина.
 *
 * <p><b>Момент записи даёт свой поставщик.</b> Умолчание аудита отдаёт
 * {@code LocalDateTime}, а audit-поля объявлены {@code OffsetDateTime}
 * (шкала одна — UTC, docs/rules/time-utc.md): без своего поставщика
 * КАЖДАЯ запись падает на «Cannot convert unsupported date type».
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditConfig {

    /** Имя писателя торговых строк. */
    private static final String WRITER = "trading-core";

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
