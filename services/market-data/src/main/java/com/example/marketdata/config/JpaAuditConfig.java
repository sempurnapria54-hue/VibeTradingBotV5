package com.example.marketdata.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {

    /** Имя писателя строк рыночных данных. */
    private static final String WRITER = "market-data";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(WRITER);
    }
}
