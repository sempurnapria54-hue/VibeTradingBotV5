package com.example.tradingbot.domain.service.deal.state_machine;

import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.market.MarketPhase;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.strategy.Strategy;
import com.example.tradingbot.domain.model.strategy.StrategyDetails;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class DealContext {

    /**
     * Биржа.
     */
    private Exchange exchange;

    /**
     * Инструмент
     */
    private Instrument instrument;

    /**
     * Главный агрегат сделки.
     * <p>
     * Именно его статусами управляет state machine.
     */
    private Deal deal;

    /**
     * Входной обычный ордер сделки.
     * <p>
     * После ENTRY_FINALIZED этот ордер становится исторической записью,
     * а основным объектом сопровождения становится позиция.
     */
    private Order entryOrder;

    /**
     * Текущая активная позиция сделки.
     * <p>
     * Если позиции нет, значит либо:
     * - вход ещё не подтверждён,
     * - либо сделка уже закрыта.
     */
    private Position activePosition;

    /**
     * Активные algo-ордера сделки.
     * <p>
     * Обычно это:
     * - основной Stop-Loss,
     * - Take-Profit,
     * - trailing и другие защитные ордера.
     */
    private List<AlgoOrder> activeAlgoOrders;

    /**
     * Текущая фаза рынка по инструменту.
     * <p>
     * Используется стратегией и handler’ами для принятия решений.
     */
    private MarketPhase marketPhase;

    /**
     * Активная стратегия по инструменту.
     * <p>
     * Это контейнер правил торговли для данного инструмента.
     */
    private Strategy strategy;

    /**
     * Детали стратегии для текущей фазы рынка.
     * <p>
     * Это именно те настройки, по которым сейчас должна работать сделка:
     * тип входа, тип защиты, риск, maxLeverage и т.д.
     */
    private StrategyDetails strategyDetails;

    /**
     * Вернуть идентификатор инструмента сделки.
     */
    public Long getInstrumentId() {
        if (this.deal == null) {
            return null;
        }

        return this.deal.getInstrumentId();
    }

    /**
     * Есть ли входной ордер.
     */
    public boolean hasEntryOrder() {
        return this.entryOrder != null;
    }

    /**
     * Финализирован ли входной ордер.
     * <p>
     * Здесь intentionally не храним boolean-поле, а вычисляем факт
     * по текущему состоянию ордера.
     */
    public boolean isEntryOrderFinal() {
        if (this.entryOrder == null) {
            return false;
        }

        return this.entryOrder.getStatus() == Order.Status.COMPLETED
                || this.entryOrder.getStatus() == Order.Status.CLOSED
                || this.entryOrder.getStatus() == Order.Status.PARTIALLY_COMPLETED;
    }

    /**
     * Подтверждена ли активная позиция.
     */
    public boolean hasActivePosition() {
        if (this.activePosition == null) {
            return false;
        }

        if (this.activePosition.getStatus() != Position.Status.ACTIVE) {
            return false;
        }

        if (this.activePosition.getSize() == null) {
            return false;
        }

        return this.activePosition.getSize()
                                  .signum() > 0;
    }

    /**
     * Закрыта ли позиция.
     * <p>
     * Это полезный derived-факт для перехода из MANAGING в EXIT_PENDING.
     */
    public boolean isPositionClosed() {
        return !hasActivePosition();
    }

    /**
     * Вернуть активный attached Stop-Loss, если он есть внутри entryOrder.
     * <p>
     * Отдельное поле attachedAlgo в контексте не нужно —
     * attached-защита является частью entryOrder.
     */
    public AttachedAlgoOrder getActiveAttachedStopLoss() {
        if (this.entryOrder == null || this.entryOrder.getAttachedAlgoOrders() == null) {
            return null;
        }

        return this.entryOrder.getAttachedAlgoOrders()
                              .stream()
                              .filter(Objects::nonNull)
                              .max(Comparator.comparing(item -> item.getCreatedAt(),
                                                        Comparator.nullsLast(Comparator.naturalOrder())))
                              .orElse(null);
    }

    /**
     * Есть ли подтверждённая attached-защита на входе.
     * <p>
     * В текущей архитектуре это страховка до переключения
     * на основную algo-защиту.
     */
    public boolean hasAttachedStopLoss() {
        return getActiveAttachedStopLoss() != null;
    }

    /**
     * Есть ли основная защита по позиции.
     * <p>
     * Тут intentionally не детализируем тип защиты —
     * handler’ы и сервисы могут проверять нужные роли точнее.
     */
    public boolean hasMainProtection() {
        return this.activeAlgoOrders != null && !this.activeAlgoOrders.isEmpty();
    }

    /**
     * Снята ли attached-защита.
     * <p>
     * После успешного PROTECTION_SWITCHED attached-защиты быть уже не должно.
     */
    public boolean isAttachedRemoved() {
        return !hasAttachedStopLoss();
    }

    /**
     * Готов ли контекст к переходу в ENTRY_SUBMITTED.
     * <p>
     * Минимально:
     * - стратегия активна,
     * - есть strategyDetails для текущей фазы,
     * - входной ордер ещё не создан,
     * - позиции нет.
     */
    public boolean isReadyForEntrySubmission() {
        return this.deal != null
                && this.strategy != null
                && this.strategyDetails != null
                && !hasEntryOrder()
                && !hasActivePosition();
    }

    /**
     * Готов ли контекст к переходу в PROTECTION_SWITCHED.
     * <p>
     * Минимально:
     * - ордер финализирован,
     * - позиция подтверждена.
     * <p>
     * Attached-защита может быть подтверждена или отсутствовать как инцидент —
     * это уже решается handler’ом.
     */
    public boolean isReadyForProtectionSwitch() {
        return isEntryOrderFinal() && hasActivePosition();
    }

    /**
     * Готов ли контекст к сопровождению позиции.
     * <p>
     * Минимально:
     * - позиция есть,
     * - основная защита есть,
     * - attached уже снят.
     */
    public boolean isReadyForManaging() {
        return hasActivePosition()
                && hasMainProtection()
                && isAttachedRemoved();
    }

    /**
     * Готов ли контекст к финализации закрытия сделки.
     * <p>
     * Минимально:
     * - позиция закрыта.
     * <p>
     * Более строгие проверки (fills/history/closeReason)
     * лучше делать в handler’е или сервисе финализации.
     */
    public boolean isReadyForExitPending() {
        return isPositionClosed();
    }
}