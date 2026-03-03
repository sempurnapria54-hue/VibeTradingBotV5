package com.example.tradingbot.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "deals", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_internal_id",
                columnNames = {"internal_id"}
        )
})
public class DealEntity {

    /**
     * Внутренний идентификатор.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Межсервисный идентификатор.
     */
    @Column(name="internal_id", nullable=false, updatable=false)
    private String internalId;

    /**
     * Идентификатор инструмента.
     */
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    /**
     * Текущий внутренний статус.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    /**
     * Причина закрытия.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    private CloseReason closeReason;

    /**
     * Сколько принесла сделка. Если убыток, то отрицательное число.
     */
    @Column(name = "result_profit", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal resultProfit;

    /**
     * Список ордеров.
     */
    @OneToMany(mappedBy = "deal", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<OrderEntity> orderEntities;

    /**
     * Список алго-ордеров.
     */
    @OneToMany(mappedBy = "deal", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AlgoOrderEntity> algoOrderEntities;

    /**
     * Список позиций.
     */
    @OneToMany(mappedBy = "deal", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<PositionEntity> positionEntities;

    public enum Status {

        /**
         * Сделка создана локально, но входной ордер ещё не отправлен.
         */
        CREATED,

        /**
         * Входной ордер отправлен на биржу (есть OrderEntity со статусом PENDING/SENT),
         * позиции ещё нет.
         */
        ORDER_PENDING,

        /**
         * Позиция появилась (PositionEntity создан/активен), но защита ещё не гарантирована
         * (attached SL может ещё не активен, отдельный SL ещё не создан/не подтверждён).
         */
        POSITION_UNPROTECTED,

        /**
         * Есть отдельный SL algo (AlgoOrderEntity) и он подтверждён биржей (PENDING/ACTIVE),
         * attached SL уже удалён или помечен к удалению.
         */
        POSITION_PROTECTED,

        /**
         * Позиция открыта и бот её сопровождает (трейлинг/правила стратегии активны).
         * (Можно объединить с POSITION_OPEN_PROTECTED, но удобно отделять как "рабочий режим")
         */
        ACTIVE,

        /**
         * Инициирован выход: отправлен закрывающий ордер / close-position,
         * ожидаем подтверждения, что позиции нет.
         */
        EXIT_PENDING,

        /**
         * Сделка завершена успешно: позиция закрыта, активных ордеров/алго от сделки нет.
         */
        CLOSED,

        /**
         * Сделка остановлена из-за ошибки/аномалии (не удалось выставить защиту,
         * не удалось реконсилировать, биржа отвечает ошибками и т.п.).
         * В этом статусе бот не продолжает торговые действия без ручного решения.
         */
        ERROR
    }

    public enum CloseReason {

        /**
         * Сработал stop-loss (обычный или algo SL).
         */
        STOP_LOSS,

        /**
         * Сработал take-profit (если используешь TP).
         */
        TAKE_PROFIT,

        /**
         * Закрыли по сигналу стратегии (ручной выход по правилам, не SL/TP).
         */
        STRATEGY_EXIT,

        /**
         * Вышли по таймауту/времени удержания (time stop).
         */
        TIME_STOP,

        /**
         * Закрыли из-за риска/ограничений (маржа, плечо, лимиты, max positions, emergency risk rule).
         */
        RISK_CONTROL,

        /**
         * Экстренная остановка (kill switch): закрыли всё и/или отменили ордера.
         */
        EMERGENCY_STOP,

        /**
         * Ручное вмешательство пользователя (закрыто в UI/вручную через API).
         */
        MANUAL,

        /**
         * Закрытие в результате реконсиляции/восстановления после рестарта
         * (например, бот обнаружил, что позиции уже нет, и завершил сделку).
         */
        RECONCILIATION,

        /**
         * Невозможно обеспечить защиту (не удалось выставить/подтвердить SL),
         * поэтому позицию закрыли принудительно (fail-safe).
         */
        PROTECTION_FAILED,

        /**
         * Биржа ликвидировала позицию.
         */
        LIQUIDATION
    }
}
