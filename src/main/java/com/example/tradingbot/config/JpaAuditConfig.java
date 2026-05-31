package com.example.tradingbot.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Включает JPA-аудит: persistence-слой проставляет системные
 * createdAt/modifiedAt/createdBy/modifiedBy на AuditableEntity.
 * Биржевые externalCreatedAt/externalModifiedAt проставляет код,
 * производящий данные (см. docs/models/domain/other/Auditable.md).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditConfig {

    private static final String SYSTEM_PRINCIPAL = "system";

    /**
     * Источник имени актора для createdBy/modifiedBy. До шага
     * «Безопасность» (Фаза 1, шаг 9) — постоянный системный принципал;
     * с вводом аутентификации будет резолвить текущего пользователя.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(SYSTEM_PRINCIPAL);
    }
}
