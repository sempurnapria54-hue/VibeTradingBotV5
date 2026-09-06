package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки аварийного снятия живого риска
 * (docs/components/KillSwitchExecutor.md §Подтверждение).
 *
 * <p><b>Предел попыток ограничен намеренно, и его исчерпание — штатный
 * отказ, а не дефект.</b> Неподтверждённое снятие означает, что радиус
 * остаётся в жёсткой ступени с незакрытым отчётом; продолжение у этого
 * терминала одно и оно названо — доведение по вызову держателя
 * (docs/components/SafetyHoldCoordinator.md). Неограниченный повтор
 * отправлял бы рыночные закрытия каждым тиком.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kill-switch")
public class KillSwitchProperties {

    /**
     * Сколько раз ход снятия риска повторяется, пока факты не подтвердят
     * отсутствие живого риска.
     */
    private Integer maxTeardownAttempts;
}
