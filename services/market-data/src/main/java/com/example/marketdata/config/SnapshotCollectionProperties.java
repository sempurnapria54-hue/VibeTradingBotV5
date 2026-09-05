package com.example.marketdata.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация сбора невосполнимых срезов
 * (docs/processes/snapshot-collection.md).
 *
 * <p><b>Обе величины — писателя-конфигурации, и обе провизорны.</b>
 * Интервал среза и глубина стакана выведены из лимитов площадки, а не
 * назначены вкусом; направление ошибки названо: реже и мельче, чем чаще
 * и глубже — пропущенный срез не ломает потолок, перегруженный сбор
 * ломает всё остальное (docs/architecture/market-data-collection.md
 * §«Невосполнимые срезы»).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "snapshot-collection")
public class SnapshotCollectionProperties {

    /** Выключатель прохода: при false запланированный и ручной тик ничего не делают. */
    private Boolean enabled = true;

    /** Глубина книги на сторону, уровней. */
    private Integer orderBookDepth = 20;

    /**
     * Потолок числа инструментов в проходе. Усечение стабильное — по
     * порядку идентификатора: при нехватке бюджета усечённым оказывается
     * один и тот же хвост листинга, а не случайные инструменты.
     */
    private Integer passLimit = 500;
}
