package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки тика синка проекций чужих реестров
 * (docs/models/domain/core/Instrument.md §«Проекция у торгового ядра»).
 *
 * <p><b>Период тика — калибровочная величина, и её направление названо:
 * чаще, чем реже.</b> Значение живёт только в конфигурации: в двух
 * носителях калибровочные числа не хранятся
 * (docs/models/domain/core/Instrument.md §«Срок свежести проекции: величина,
 * писатель, реакция»).
 *
 * <p>Выключатель отдельным полем — конвенция джоб
 * (.claude/rules/codestyle.md §Джобы): при {@code false} и запланированный,
 * и ручной тик не делают ничего.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "projection-sync")
public class ProjectionSyncProperties {

    /** Тик синка проекций включён. */
    private Boolean enabled = Boolean.TRUE;

    /**
     * Окно чтения листинга у владельца каталога. Не больше потолка окна
     * его поверхности — иначе владелец отвергает каждое чтение, и
     * проекция каталога не сводится ни одним тиком.
     */
    private Integer listingWindow;
}
