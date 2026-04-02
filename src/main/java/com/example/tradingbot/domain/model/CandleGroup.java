package com.example.tradingbot.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroup extends Auditable {

    /**
     * Внутренний идентификатор группы свечей.
     */
    private Long id;

    /**
     * Инструмент-владелец группы свечей.
     */

    private Long instrumentId;

    /**
     * Таймфрейм группы (например 1m/5m/1H).
     */
    private String timeframe;

    /**
     * Текущий статус жизненного цикла загрузки свечей.
     */
    private Status status;

    /**
     * Время открытия первой свечи в UTC миллисекундах.
     */
    private Long coverageStartUtcMillis;

    /**
     * Время закрытия последней свечи в UTC миллисекундах.
     */
    private Long coverageEndUtcMillis;

    public enum Status {
        /**
         * Группа создана, данных нет или покрытие не подтверждено.
         */
        CREATED,
        /**
         * Идёт историческая загрузка “в глубину” до coverageStartUtcMillis.
         * Важно: в этом статусе есть checkpoints (coverageStartUtcMillis, coverageEndUtcMillis) — иначе рестарт будет дорогим.
         */
        BACKFILL,
        /**
         * Регулярная докачка хвоста (overlap последних N баров) по расписанию.
         */
        SYNC,
        /**
         * Быстрая проверка целостности по count (и при необходимости инициирует repair).
         */
        CHECK,
        /**
         * Найдены дыры → бинарный поиск по count + докачка окон.
         */
        REPAIR,
        /**
         * Покрытие подтверждено, дыры отсутствуют.
         */
        ACTIVE,
        /**
         * Автоматически продолжать нельзя (превышены попытки/фатальная ошибка). Нужен ручной разбор/сброс.
         */
        ERROR,
        /**
         * Удалён, не участвует в расчётах
         */
        DELETED,
    }

}
