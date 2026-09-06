package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки поверхности ручного управления остановкой
 * (docs/rules/manual-halt.md).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "manual-halt")
public class ManualHaltProperties {

    /**
     * Окно ТЕРМИНАЛЬНЫХ сделок радиуса, по которым предусловие снятия
     * проверяет живой риск.
     *
     * <p>Нетерминальные окна не требуют: слот пары держит не больше одной
     * незакрытой сделки. Терминальные требуют — история счёта растёт без
     * предела, а остаточный риск после терминала живёт на <b>недавно</b>
     * закрытой сделке: позиция давно закрытой сделки не воскресает.
     *
     * <p><b>Названное ограничение:</b> терминальная сделка старше окна в
     * проверку не входит, и это осознанная граница энфорсера — обратная
     * альтернатива (отказ снятия при упоре в окно) лишила бы холд выхода
     * на счёте с длинной историей, а это ровно то, что концепция называет
     * недопустимым.
     */
    private Integer clearanceTerminalWindow = 200;
}
