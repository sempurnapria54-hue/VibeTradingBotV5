package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки прохода сопровождения сделок
 * (docs/components/DealOrchestratorJob.md §«Операционная оболочка»).
 *
 * <p>Период тика читает {@code @Scheduled} напрямую из
 * {@code deal-orchestrator.cron}: величина живёт только в конфигурации, в
 * двух носителях калибровочные числа не хранятся.
 *
 * <p>Выключатель отдельным полем — конвенция джоб
 * (.claude/rules/codestyle.md §Джобы): при {@code false} и запланированный,
 * и ручной тик не делают ничего.
 *
 * <p><b>Потолка выборки движений здесь нет намеренно</b> — он читается
 * сборкой контекста и живёт в её секции {@code deal-context}: проход о нём
 * не знает и знать не обязан.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "deal-orchestrator")
public class DealOrchestratorProperties {

    /** Проход сопровождения сделок включён. */
    private Boolean enabled = Boolean.TRUE;

    /**
     * Окно выборки нетерминальных сделок за один проход.
     *
     * <p>Окно обязательно: число торговых строк растёт вместе с контуром,
     * и безлимитное чтение выросло бы вместе с ним
     * (.claude/rules/codestyle.md §«Выборка данных»). Сделки, не попавшие
     * в окно, подбирает следующий тик — порядок выборки детерминирован
     * идентификатором.
     */
    private Integer batchSize = 100;
}
