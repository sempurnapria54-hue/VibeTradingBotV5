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
 * <p><b>Автора записи здесь не производит НИКТО, и это названо, а не
 * умолчано.</b> Поставщика ({@code AuditorAware}) в этой конфигурации нет,
 * поэтому {@code createdBy}/{@code modifiedBy} остаются пустыми у обоих
 * классов — и у черновика, пришедшего поверхностью, и у порождённого
 * модулем сервиса. Объявленное различение двух классов
 * (docs/models/domain/other/Auditable.md §«Область значений актора») этим
 * НЕ исполняется: контур доступа принципала удостоверяет, но до записи
 * его никто не доносит.
 *
 * <p><b>Чем закрывается.</b> Поставщиком актора, читающим контекст хода
 * (docs/models/domain/other/Auditable.md §«Носитель дискриминатора —
 * контекст хода, а не поле модели»); он же поставляет значение в
 * содержимое трёх классов определения стратегии, у которых ручная тропа
 * есть. Носитель приезжает дельтой CODE шага 10 фазы 2
 * (.claude/work/progress/phase-2-step-10-design-pass.md, позиция 8).
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
