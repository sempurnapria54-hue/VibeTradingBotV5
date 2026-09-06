package com.example.tradingcore.config;

import com.example.tradingcore.domain.command.ServiceCommandRetryPolicy;
import com.example.tradingcore.domain.command.ServiceCommandType;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Политика повтора команд: умолчание плюс переопределения по типу
 * команды.
 *
 * <p><b>Предел повторов — операционная крутилка</b>, поэтому живёт в
 * конфигурации, а не в базе и не в коде: правка обязана браться сразу
 * везде (docs/components/RetryPolicyService.md §«Авторитет предела —
 * политика, читается живьём»).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "service-command-retry")
public class ServiceCommandRetryProperties {

    /** Политика по умолчанию — для команд без переопределения. */
    private ServiceCommandRetryPolicy defaultPolicy;

    /** Переопределения политики по типу команды. */
    private Map<ServiceCommandType, ServiceCommandRetryPolicy> policies =
            new EnumMap<>(ServiceCommandType.class);
}
