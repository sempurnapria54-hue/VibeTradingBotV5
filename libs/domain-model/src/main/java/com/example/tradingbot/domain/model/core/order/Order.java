package com.example.tradingbot.domain.model.core.order;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ordinary exchange order, связанный с Deal. Хранит локальный intent,
 * идентификаторы (internalId — stable client id + биржевой externalId),
 * доменный статус, сырой статус биржи (диагностика), цену/размер, факты
 * исполнения, embedded attached protection. Не действие стратегии:
 * связь StrategyAction ↔ Order — через DealActionState, поэтому не
 * хранит strategyActionId / role / level. См.
 * docs/models/domain/core/Order.md, docs/lifecycles/Order.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class Order extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Сделка, к которой относится ордер. */
    private Long dealId;

    /**
     * Транш, чью экспозицию заявка создаёт или гасит. Заявка принадлежит
     * траншу; deal-уровень остаётся агрегатным представлением.
     */
    private Long dealTrancheId;


    /** Межсервисный id; stable client id (OKX clOrdId). */
    private String internalId;

    /** Биржевой id ordinary order (OKX ordId). */
    private String externalId;

    /** Доменный статус. */
    private Status status;

    /** Причина финализации / перевода в ERROR. */
    private CloseReason closeReason;

    /** Бизнес-тип ordinary order (не подменяет strategy role). */
    private Type type;

    /**
     * Сторона заявки — доменный перечень, не литерал площадки.
     * Отличается от направления сделки: закрывающая заявка на длинной
     * ноге имеет сторону {@link Side#SELL}.
     */
    private Side side;

    /** Сырой статус биржи (OKX state) — диагностический факт, FSM напрямую не использует. */
    private String externalStatus;

    /** Цена (для market-like может быть null). */
    private BigDecimal price;

    /** Размер (для SWAP/FUTURES — контракты). */
    private BigDecimal size;

    /** Накопленный исполненный объём. */
    private BigDecimal accumulatedFillSize;

    /** Средняя цена исполнения. */
    private BigDecimal averagePrice;

    /** Накопленная комиссия. */
    private BigDecimal fee;

    /** Доменное намерение: ордер только уменьшает позицию (не из снапшота биржи). */
    private Boolean positionReducingOnly;

    /** internalId предшественника в цепочке REPLACE (nullable; обратная ссылка выводится запросом). */
    private String replacesInternalId;

    /**
     * Эпизод сделки, к которому относится заявка. Пусто, пока эпизода
     * нет либо заявка его не открыла. Write-once — <b>ось отбора</b>
     * живого эпизода в числах риска: без неё ноги закрытых эпизодов
     * неотличимы от ног текущего, и пара «взятое ↔ снятое защитой»
     * считалась бы по всей истории сделки
     * (docs/spec/deal-risk-numbers.json, {@code onLiveEpisode}).
     */
    private Long positionId;

    /**
     * Цена входа, по которой считался риск ЭТОЙ ноги. У рыночного входа
     * — расчётная референс-цена, на биржу не отправляемая. Write-once.
     */
    private BigDecimal plannedEntryPrice;

    /** Заявленный размер этой ноги в контрактах. Write-once. */
    private BigDecimal plannedSizeContracts;

    /** Плановый риск этой ноги — убыток на её стопе при постановке. Write-once. */
    private BigDecimal plannedRiskAmount;

    /** Валюта планового риска ноги. Write-once. */
    private String plannedRiskCurrency;

    /** Размер контракта на момент постановки ноги. Write-once. */
    private BigDecimal plannedContractValue;

    /** Уровень стопа, под который считался риск ноги. Write-once. */
    private BigDecimal plannedStopPrice;

    /** Embedded attached protection, созданная вместе с parent order. */
    private List<AttachedAlgoOrder> attachedAlgoOrders;

    private static final Set<Status> LIVE_STATUSES =
            EnumSet.of(Status.CREATED, Status.PENDING, Status.ACTIVE, Status.PARTIALLY_COMPLETED);

    /** Live: ещё существует на бирже / влияет на risk (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED). */
    public Boolean isLive() {
        return LIVE_STATUSES.contains(status);
    }

    /** Есть хотя бы одна active-like (PENDING/ACTIVE) attached-защита. */
    public Boolean hasActiveAttachedProtection() {
        return isNotEmpty(attachedAlgoOrders)
                && attachedAlgoOrders.stream().anyMatch(protection -> isTrue(protection.isActiveLike()));
    }

    /** Полностью исполнен: COMPLETED + closeReason FILLED (write-once). */
    public void toComplete() {
        this.status = Status.COMPLETED;
        applyCloseReason(CloseReason.FILLED);
    }

    /** Отменён: требует ненулевой reason. */
    public void toCancel(CloseReason reason) {
        requireReason(reason);
        this.status = Status.CANCELED;
        applyCloseReason(reason);
    }

    /** Ошибочное состояние: требует ненулевой reason. */
    public void toError(CloseReason reason) {
        requireReason(reason);
        this.status = Status.ERROR;
        applyCloseReason(reason);
    }

    private void applyCloseReason(CloseReason reason) {
        if (isNull(closeReason)) {
            this.closeReason = reason;
        }
    }

    private void requireReason(CloseReason reason) {
        if (isNull(reason)) {
            throw new IllegalArgumentException("closeReason is required");
        }
    }

    /**
     * Сторона заявки.
     *
     * <p><b>Перечень доменный, а не источниковый, и это несущее
     * свойство.</b> Класс живёт в общей библиотеке, чей объявленный
     * критерий устойчивости — «вторая площадка проходит добавлением, а
     * не правкой существующих полей» (docs/architecture/services.md).
     * Поле, хранящее словарь одной площадки, нарушало бы его по
     * построению: у второй площадки словарь свой, и совпадение написания
     * ничем не гарантировано. Перевод в словарь площадки делает коннектор
     * на своей границе (docs/models/mapping/Order.md).
     *
     * <p>Перечень закрыт: третьей стороны у заявки не бывает. Уменьшение
     * позиции стороной не выражается — для него есть отдельное поле
     * {@code positionReducingOnly}.
     */
    public enum Side {

        /** Покупка: заявка увеличивает длинную экспозицию либо уменьшает короткую. */
        BUY,

        /** Продажа: заявка увеличивает короткую экспозицию либо уменьшает длинную. */
        SELL
    }

    /** Бизнес-тип ordinary order (не подменяет strategy role). */
    public enum Type {

        /** Входной ордер без встроенной защиты. */
        ENTRY,

        /** Входной ордер со встроенным attached stop-loss. */
        ENTRY_ATTACHED_STOP_LOSS
    }

    /** Доменный статус ordinary order. Значения и переходы — docs/lifecycles/Order.md. */
    public enum Status {

        /** Локальная сущность создана, на биржу не отправлена. */
        CREATED,

        /** Отправлен на биржу, факт постановки не подтверждён. */
        PENDING,

        /** Активен на бирже (live). */
        ACTIVE,

        /** Частично исполнен. */
        PARTIALLY_COMPLETED,

        /** Полностью исполнен. */
        COMPLETED,

        /** Отменён. */
        CANCELED,

        /** Ошибка. */
        ERROR
    }

    /** Причина финализации ordinary order / перевода в ERROR. */
    public enum CloseReason {

        /** Полностью исполнен. */
        FILLED,

        /** Отменён стратегией. */
        CANCELED_BY_STRATEGY,

        /** Стратегия заменила другим ордером (REPLACE-ремодел; симметрично AlgoOrder). */
        REPLACED_BY_STRATEGY,

        /** Аварийный safety-flow / kill-switch. */
        KILL_SWITCH,

        /** Условие создания/ожидания ордера больше неактуально (штатно, не ошибка). */
        CONDITION_EXPIRED,

        /** Не найден после refresh/search/history цикла. */
        MISSING_AFTER_REFRESH,

        /** Неизвестный внешний статус. */
        UNKNOWN_EXTERNAL_STATUS,

        /** Нарушение exchange-инварианта. */
        EXCHANGE_INVARIANT_VIOLATION,

        /** Fallback. */
        UNKNOWN
    }
}
