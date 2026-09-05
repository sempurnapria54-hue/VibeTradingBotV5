package com.example.auth.config;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Оси окружения, которые сервис обязан знать
 * (docs/architecture/platform.md §«Чем различаются окружения»).
 *
 * <p>Значения приезжают из манифеста окружения (`deploy/<окружение>/env.yaml`),
 * а не назначаются здесь: перечень осей закрыт домом, и сервис его читает,
 * а не переобъявляет.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "platform.environment")
public class EnvironmentProperties {

    /** Имя окружения — `dev`, `stage`, `prod`. */
    private String name;

    /**
     * Контуры площадки, допустимые в этом окружении.
     *
     * <p>Непроизводственное окружение допускает только {@code DEMO}: за
     * потерю в нём никто не отвечает, поэтому капитала оно не двигает.
     * Исполнимая форма правила — docs/spec/environment-contour.json,
     * величина {@code contourAdmitted}.
     */
    private Set<ExchangeAccount.Contour> admittedContours;
}
