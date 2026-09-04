package com.example.tradingbot.domain.model.core.position;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Строка одного ЭПИЗОДА позиции сделки: одна биржевая позиция — одна
 * строка. Отвечает на вопрос «есть ли живой риск по сделке прямо
 * сейчас», а после закрытия несёт ПОЛОЖЕНИЕ ЗАКРЫТИЯ — реализованные
 * факты из истории позиций. Сделка многоэпизодна: позиция может
 * схлопнуться в ноль и открыться заново; живой эпизод один, закрытые
 * остаются строками той же таблицы.
 *
 * <p>Адресуемая единица эпизода — ПАРА (externalId, externalCreatedAt):
 * биржевой идентификатор переиспользуется у переоткрытой позиции, и
 * одного его недостаточно. Создаётся/наполняется только исполнителем
 * добычи состояния позиции (нога 1 заводит, нога 2 наполняет положением
 * закрытия). См. docs/models/domain/core/Position.md,
 * docs/lifecycles/Position.md.
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

    /** Положение закрытия: готовый net закрытой позиции, посчитанный биржей. */
    private BigDecimal externalRealizedProfit;

    /** Положение закрытия: валюта этого числа — проверяемый признак, не источник валюты результата. */
    private String externalResultCurrency;

    /** Положение закрытия: средняя цена фактического выхода. */
    private BigDecimal externalCloseAveragePrice;

    /** Положение закрытия: сырой тип последнего закрытия источника; персистится всегда. */
    private String externalCloseType;

    /** Положение закрытия: результат до издержек — правый операнд первой пары сверки. */
    private BigDecimal externalRealizedProfitGross;

    /** Положение закрытия: знаковая комиссионная компонента, СЫРОЙ знак — вторая пара сверки. */
    private BigDecimal externalFee;

    /** Положение закрытия: финансирование эпизода, знак НОРМАЛИЗОВАН (издержка положительна) — третья пара. */
    private BigDecimal externalFundingCost;

    /** Положение закрытия: штраф ликвидации, СЫРОЙ знак — четвёртая пара сверки. */
    private BigDecimal externalLiquidationPenalty;

    /** Live market risk: ACTIVE и externalSize > 0 (ACTIVE сам по себе риска не гарантирует). */
    public Boolean hasLiveRisk() {
        return Objects.equals(Status.ACTIVE, status)
                && nonNull(externalSize)
                && externalSize.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Эпизод закрыт. */
    public Boolean isClosed() {
        return Objects.equals(Status.CLOSED, status);
    }

    /**
     * Запись закрытия эпизода добыта. Носитель предиката — непустота
     * готового net'а: он контрактное поле границы, и запись, его не
     * несущая, границу не проходит вовсе
     * (docs/models/domain/core/Position.md).
     */
    public Boolean closeRecordFetched() {
        return nonNull(externalRealizedProfit);
    }

    /** Строка ждёт положения закрытия: закрыта и записи закрытия не несёт. */
    public Boolean awaitsCloseRecord() {
        return isTrue(isClosed()) && isFalse(closeRecordFetched());
    }

    /**
     * Тот же эпизод, что наблюдённый: совпала ПАРА (биржевой
     * идентификатор, биржевое время создания). Одного идентификатора
     * недостаточно — источник переиспользует его у переоткрытой позиции.
     */
    public Boolean sameEpisode(String observedExternalId, OffsetDateTime observedCreatedAt) {
        return Objects.equals(externalId, observedExternalId)
                && Objects.equals(getExternalCreatedAt(), observedCreatedAt);
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

        /** Закрылась на стороне биржи без текущей команды close (SL/TP/trailing/liquidation/ADL). */
        EXTERNAL_CLOSE,

        /** Problem reason для ERROR: adapter обнаружил нарушение exchange-инварианта. */
        EXCHANGE_INVARIANT_VIOLATION
    }
}
