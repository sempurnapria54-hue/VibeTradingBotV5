package com.example.auditstatistics.config;

import com.example.auditstatistics.domain.model.JournalRetentionProfile;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Оси окружения, которые сервис обязан знать
 * (docs/architecture/platform.md §«Чем различаются окружения»).
 *
 * <p>Значения приезжают из манифеста окружения
 * (`deploy/<окружение>/env.yaml` → ключ конфигурации манифеста сервиса), а
 * не назначаются здесь: перечень осей закрыт домом, и сервис его читает, а
 * не переобъявляет.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "platform.environment")
public class EnvironmentProperties {

    /** Имя окружения — `dev`, `stage`, `prod`. */
    private String name;

    /**
     * Профиль хранения журнала аудита в этом окружении.
     *
     * <p><b>Ось обязана дойти до исполнителя</b> — иначе предикат
     * применимости чистки не вычисляется ничем
     * (docs/models/domain/other/AuditRecord.md §«Глубина хранения»);
     * тропа — ключ конфигурации манифеста сервиса, как допустимые контуры
     * площадки доезжают до `auth`.
     *
     * <p><b>Пустое значение означает «ось не доехала», а не «не
     * чистить»</b> (docs/rules/absent-value-semantics.md). Как на него
     * реагирует чистка — предмет её дома
     * (docs/components/JournalCleanupJob.md §«Ось не доехала — проход не
     * идёт, и это не то же самое, что `UNBOUNDED`»), а не этой формы.
     */
    private JournalRetentionProfile journalRetentionProfile;
}
