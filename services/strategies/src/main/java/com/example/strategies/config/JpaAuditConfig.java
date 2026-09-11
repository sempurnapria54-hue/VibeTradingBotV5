package com.example.strategies.config;

import com.example.strategies.domain.service.ActorProvider;
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
 * запись падает на «Cannot convert unsupported date type».
 *
 * <p><b>Автора записи производит {@link ActorProvider}</b>, и он же
 * поставляет актора в содержимое трёх классов событий определения: у
 * величины один носитель на обоих читателей
 * (docs/models/domain/other/Auditable.md §«Носитель дискриминатора —
 * контекст хода, а не поле модели»). Объявленное различение двух классов —
 * имя принципала против класса контура — исполняется им, а не умолчанием
 * каркаса: прежде поставщика не было вовсе, и {@code createdBy} оставался
 * пустым у обоих классов записей.
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
     * Резолвер актора записи. Собственного правила не держит — зовёт
     * единственного поставщика, чтобы у ответа «кто инициировал ход» не
     * появилось второй редакции.
     */
    @Bean
    public AuditorAware<String> auditorAware(ActorProvider actorProvider) {
        return () -> Optional.of(actorProvider.currentActor());
    }
}
