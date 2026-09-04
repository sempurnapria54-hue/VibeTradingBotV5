package com.example.tradingbot.config;

import com.example.tradingbot.domain.command.ServiceCommandRetryPolicy;
import com.example.tradingbot.domain.command.ServiceCommandType;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация retry-политики команд (секция service-command-retry):
 * default-policy + per-command policies. На первом этапе политика — в
 * конфиге приложения, не в БД. См. docs/components/RetryPolicyService.md.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "service-command-retry")
public class ServiceCommandRetryProperties {

    /** Политика по умолчанию (fallback для команд без override). */
    private ServiceCommandRetryPolicy defaultPolicy;

    /** Переопределения политики по типу команды. */
    private Map<ServiceCommandType, ServiceCommandRetryPolicy> policies = new EnumMap<>(ServiceCommandType.class);
}
