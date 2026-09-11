package com.example.tradingcore.config;

import com.example.tradingcore.domain.service.ActorProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Включает JPA auditing: системные audit-поля строк проставляет
 * персистентность (.claude/rules/codestyle.md §«Auditable по слоям»).
 *
 * <p><b>Автор записи резолвится поставщиком актора, а не константой.</b>
 * Ходы ядра бывают обоих классов: проход оркестратора и исполнитель команды
 * внешнего инициатора не имеют по построению, а ручная остановка приходит
 * от держателя через поверхность — и запись, созданная человеком с пометкой
 * «собственный проход», утверждает неверное
 * (docs/models/domain/other/Auditable.md §«Область значений актора»). Своей
 * редакции правила конфигурация не держит — она зовёт единственного
 * поставщика.
 *
 * <p><b>Момент записи даёт свой поставщик.</b> Умолчание аудита отдаёт
 * {@code LocalDateTime}, а audit-поля объявлены {@code OffsetDateTime}
 * (шкала одна — UTC, docs/rules/time-utc.md): без своего поставщика
 * КАЖДАЯ запись падает на «Cannot convert unsupported date type».
 */
@Configuration
@RequiredArgsConstructor
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditConfig {

    private final ActorProvider actorProvider;

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(actorProvider.currentActor());
    }

    /** Момент записи — всегда в UTC, как требует шкала времени системы. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
