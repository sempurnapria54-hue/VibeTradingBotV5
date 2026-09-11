package com.example.auditstatistics.config;

import com.example.auditstatistics.domain.service.ActorProvider;
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
 * <p><b>Носитель полей аудита у процесса сегодня один</b> — строка
 * отвергнутого вызова (docs/models/domain/other/AccessDenial.md). Строка
 * журнала событий их не несёт вовсе: событие произошло однажды, и правок у
 * записи о нём не бывает.
 *
 * <p><b>Момент записи даёт свой поставщик.</b> Умолчание аудита отдаёт
 * {@code LocalDateTime}, а audit-поля объявлены {@code OffsetDateTime}
 * (шкала одна — UTC, docs/rules/time-utc.md): без своего поставщика КАЖДАЯ
 * запись падает на «Cannot convert unsupported date type».
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditConfig {

    /** Момент записи — всегда в UTC, как требует шкала времени системы. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Резолвер актора записи. Значение отдаёт доменный поставщик
     * ({@link ActorProvider}), а не приватный метод конфигурации: ответ
     * «кто инициировал ход» доменный, и у соседей он выражен тем же
     * способом (docs/models/domain/other/Auditable.md §«Носитель
     * дискриминатора — контекст хода, а не поле модели»).
     *
     * <p><b>Здесь остаётся только тропа JPA-аудита.</b> Классы значений,
     * их признак и обе тропы отказа доступа названы у поставщика; второй
     * их носитель разошёлся бы с первым первой же правкой
     * (.claude/rules/policy-home.md).
     */
    @Bean
    public AuditorAware<String> auditorAware(ActorProvider actorProvider) {
        return () -> Optional.of(actorProvider.currentActor());
    }
}
