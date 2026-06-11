package com.example.tradingbot.domain.model.core.position;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Позиция внутри Deal — runtime-сущность live-risk. Хранит только
 * данные сопровождения live-risk позиции; итоговый PnL, история команд,
 * fills, raw exchange response — не здесь (Deal.resultProfit считается
 * через REFRESH_FILLS). Создаётся/обновляется только REFRESH_POSITION
 * executor'ом. Связь со StrategyAction — через DealActionState, поэтому
 * не хранит strategyActionId / instrumentId / exchangeId / internalId.
 * См. docs/models/domain/core/Position.md, docs/lifecycles/Position.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class Position extends Auditable {

    /** Внутренний идентификатор позиции в БД. */
    private Long id;

    /** Сделка, в рамках которой сопровождается позиция. */
    private Long dealId;

    /** Биржевой ID позиции, если биржа его отдаёт (OKX posId). Не stable client id. */
    private String externalId;

    /** Доменный статус. */
    private Status status;

    /** Причина закрытия / problem reason (не дублирует Deal.CloseReason). */
    private CloseReason closeReason;

    /** Доменное направление рыночного риска. */
    private Direction direction;

    /** Размер по данным биржи, нормализованный абсолют (abs(pos)). */
    private BigDecimal externalSize;

    /** Средняя цена входа. */
    private BigDecimal externalAverageEntryPrice;

    /** Mark price. */
    private BigDecimal externalMarkPrice;

    /** Расчётная цена ликвидации. */
    private BigDecimal externalLiquidationPrice;

    /** Маржа позиции. */
    private BigDecimal externalMargin;

    /** Нереализованный PnL. */
    private BigDecimal externalUnrealizedProfit;

    /** Live market risk: ACTIVE и externalSize > 0 (ACTIVE сам по себе риска не гарантирует). */
    public Boolean hasLiveRisk() {
        return Objects.equals(Status.ACTIVE, status)
                && nonNull(externalSize)
                && externalSize.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Направление рыночного риска позиции. NET не используется как
     * direction (это режим биржи, не направление риска).
     */
    public enum Direction {

        /** Длинная позиция. */
        LONG,

        /** Короткая позиция. */
        SHORT
    }

    /** Доменный статус позиции. Значения и переходы — docs/lifecycles/Position.md. */
    public enum Status {

        /** Позиция открыта/сопровождается (live risk при externalSize > 0). */
        ACTIVE,

        /** Позиция закрыта. */
        CLOSED,

        /** Проблемное состояние (нарушение exchange-инварианта). */
        ERROR
    }

    /** Причина закрытия позиции (механизм); не дублирует Deal.CloseReason. */
    public enum CloseReason {

        /** Штатное закрытие как действие стратегии. */
        CLOSED_BY_STRATEGY,

        /** Аварийный safety-flow / kill-switch. */
        KILL_SWITCH,

        /** Ручное закрытие пользователем. */
        MANUAL_CLOSE,

        /** Закрылась на стороне биржи без текущей команды close (SL/TP/trailing/liquidation/ADL). */
        EXTERNAL_CLOSE,

        /** Problem reason для ERROR: adapter обнаружил нарушение exchange-инварианта. */
        EXCHANGE_INVARIANT_VIOLATION,

        /** Fallback, если причину безопасно определить не удалось. */
        UNKNOWN
    }
}
