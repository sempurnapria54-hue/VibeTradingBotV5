package com.example.tradingbot.domain.model.aggregate.deal;

/**
 * Сделка — бизнес-цикл одной торговой идеи (агрегат). Полная модель
 * дозревает на шагах 4/7 Фазы 1 (команды, P&L, оркестрация); на шаге 2
 * класс несёт только {@link Status} — ключ группировки шагов стратегии
 * ({@code StrategyDetail.stepsByStatus}). См.
 * docs/models/domain/aggregate/Deal.md, docs/lifecycles/Deal.md.
 */
public class Deal {

    /**
     * FSM-статус сделки: бизнес-этап, не статус Order/AlgoOrder/Position
     * и не exchange ACK. Значения, группы и переходы —
     * docs/lifecycles/Deal.md.
     */
    public enum Status {

        /** Кандидат: повторная проверка entry-условий до live-риска. */
        PRECHECK,

        /** Входной ордер отправлен на биржу. */
        ENTRY_SUBMITTED,

        /** Входной ордер финализирован (исполнен/частично — позиция есть). */
        ENTRY_FINALIZED,

        /** Защита переключена с attached на standalone. */
        PROTECTION_SWITCHED,

        /** Сопровождение открытой позиции. */
        MANAGING,

        /** Запущен выход — ждём завершения закрывающих действий. */
        EXIT_PENDING,

        /** Сделка завершена штатно. */
        CLOSED,

        /** Ошибка цикла сделки. */
        ERROR,

        /** Сделка завершена аварийно (emergency close). */
        EMERGENCY_CLOSED
    }
}
