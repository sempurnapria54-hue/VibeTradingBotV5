package com.example.strategies.config;

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
 * запись падает на «Cannot convert unsupported date type».
 *
 * <p><b>Автор записи различает два класса, и различает их контур
 * доступа.</b> Черновик, пришедший поверхностью, несёт имя предъявленного
 * принципала; черновик, порождённый модулем сервиса (советник, фаза 7), —
 * класс собственного прохода. Это и есть объявленное различение источника
 * черновика: отдельного поля под него не заводится
 * (docs/models/domain/other/Auditable.md §«Область значений актора»,
 * docs/architecture/reserved-extension-points.md §«Принцип
 * резервирования»).
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
