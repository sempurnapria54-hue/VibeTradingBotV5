package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки реле outbox (docs/components/OutboxRelayJob.md §«Форма —
 * штатная джоба контура»).
 *
 * <p><b>Период — калибровочная величина сервиса.</b> Ошибка меряется парой
 * «задержка публикации против частоты пустых проходов», а не размером
 * потери; направление названо: ЧАЩЕ, чем реже — задержка публикации есть
 * задержка наблюдаемости, а пустой проход стои́т одного запроса к своей
 * базе. Значение живёт только в конфигурации.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "outbox-relay")
public class OutboxRelayProperties {

    /** Реле включено. */
    private Boolean enabled = Boolean.TRUE;

    /**
     * Окно чтения неопубликованных строк за один тик.
     *
     * <p>Упор в окно неполнотой прохода не считается: остаток заберёт
     * следующий тик, а наблюдаемость даёт метрика глубины outbox, а не
     * пропуск.
     */
    private Integer batchSize = 200;
}
