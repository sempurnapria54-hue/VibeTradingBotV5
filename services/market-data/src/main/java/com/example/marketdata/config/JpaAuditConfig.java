package com.example.marketdata.config;

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
 * <p><b>Автора записи производит {@link ActorProvider}</b>: область значений
 * актора закрыта и знает два класса — имя предъявленного принципала либо
 * класс собственного прохода контура
 * (docs/models/domain/other/Auditable.md §«Область значений актора»). Ряды
 * рынка пишет джоба, и её строка получает класс контура; ручной триггер той
 * же джобы порождён человеком, и его строка получает имя принципала — контекст
 * хода до треда фасада доносит {@link AsyncActorContextConfigurer}. Прежде
 * здесь стояло имя сервиса — третье значение, которого область не знает и
 * которое с появлением именованных принципалов стало бы неотличимо от них.
 *
 * <p><b>Момент записи даёт свой поставщик, и это не украшение.</b>
 * Умолчание аудита отдаёт {@code LocalDateTime}, а audit-поля объявлены
 * {@code OffsetDateTime} (шкала одна — UTC, docs/rules/time-utc.md): без
 * своего поставщика КАЖДАЯ запись падает на «Cannot convert unsupported
 * date type». Обнаружено первым живым прогоном на стенде — синк листинга
 * не сохранил ни одной строки.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditConfig {

    /**
     * Резолвер актора записи. Собственного правила не держит — зовёт
     * единственного поставщика, чтобы у ответа «кто инициировал ход» не
     * появилось второй редакции.
     */
    @Bean
    public AuditorAware<String> auditorAware(ActorProvider actorProvider) {
        return () -> Optional.of(actorProvider.currentActor());
    }

    /** Момент записи — всегда в UTC, как требует шкала времени системы. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
