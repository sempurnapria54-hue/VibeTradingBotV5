package com.example.tradingbot.domain.service.deal.state_machine;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market.MarketPhase;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.BooleanUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

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

    private List<Order> orders;

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

    private List<AlgoOrder> algoOrders;

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
     * phaseEntryPolicy, риск, maxLeverage и шаги стратегии по Deal.Status.
     */
    private StrategyDetails strategyDetails;

    /**
     * Вернуть идентификатор инструмента сделки.
     */
    public Long getInstrumentId() {
        if (Objects.isNull(this.deal)) {
            return null;
        }

        return this.deal.getInstrumentId();
    }

    /**
     * Есть ли входной ордер.
     */
    public boolean hasEntryOrder() {
        return Objects.nonNull(this.entryOrder);
    }

    /**
     * Финализирован ли входной ордер.
     * <p>
     * Здесь intentionally не храним boolean-поле, а вычисляем факт
     * по текущему состоянию ордера.
     */
    public boolean isEntryOrderFinal() {
        if (Objects.isNull(this.entryOrder)) {
            return false;
        }

        return Objects.equals(this.entryOrder.getStatus(), Order.Status.COMPLETED)
                || Objects.equals(this.entryOrder.getStatus(), Order.Status.CLOSED)
                || Objects.equals(this.entryOrder.getStatus(), Order.Status.PARTIALLY_COMPLETED);
    }

    /**
     * Подтверждена ли активная позиция.
     */
    public boolean hasActivePosition() {
        if (Objects.isNull(this.activePosition)) {
            return false;
        }

        if (BooleanUtils.isFalse(Objects.equals(this.activePosition.getStatus(), Position.Status.ACTIVE))) {
            return false;
        }

        if (Objects.isNull(this.activePosition.getSize())) {
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
        return BooleanUtils.isFalse(hasActivePosition());
    }

    /**
     * Вернуть активный attached Stop-Loss, если он есть внутри entryOrder.
     * <p>
     * Отдельное поле attachedAlgo в контексте не нужно —
     * attached-защита является частью entryOrder.
     */
    public AttachedAlgoOrder getActiveAttachedStopLoss() {
        if (Objects.isNull(this.entryOrder) || Objects.isNull(this.entryOrder.getAttachedAlgoOrders())) {
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
        return Objects.nonNull(getActiveAttachedStopLoss());
    }

    public boolean hasAttachedProtection() {
        return hasAttachedStopLoss();
    }

    /**
     * Есть ли основная защита по позиции.
     * <p>
     * Тут intentionally не детализируем тип защиты —
     * handler’ы и сервисы могут проверять нужные роли точнее.
     */
    public boolean hasMainProtection() {
        return BooleanUtils.isFalse(emptyIfNull(this.activeAlgoOrders).isEmpty());
    }

    /**
     * Снята ли attached-защита.
     * <p>
     * После успешного PROTECTION_SWITCHED attached-защиты быть уже не должно.
     */
    public boolean isAttachedRemoved() {
        return BooleanUtils.isFalse(hasAttachedStopLoss());
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
        return Objects.nonNull(this.deal)
                && Objects.nonNull(this.strategy)
                && Objects.nonNull(this.strategyDetails)
                && BooleanUtils.isFalse(hasEntryOrder())
                && BooleanUtils.isFalse(hasActivePosition());
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

    public boolean isEntryOrderFinalized() {
        return isEntryOrderFinal();
    }

    public Order findOrderByStrategyActionId(Long strategyActionId) {
        if (Objects.isNull(strategyActionId)) {
            return null;
        }

        return safeOrders().stream()
                           .filter(Objects::nonNull)
                           .filter(order -> Objects.equals(order.getStrategyActionId(), strategyActionId))
                           .findFirst()
                           .orElse(null);
    }

    public boolean hasOrderByStrategyActionId(Long strategyActionId) {
        return Objects.nonNull(findOrderByStrategyActionId(strategyActionId));
    }

    public AlgoOrder findAlgoOrderByStrategyActionId(Long strategyActionId) {
        if (Objects.isNull(strategyActionId)) {
            return null;
        }

        return safeAlgoOrders().stream()
                               .filter(Objects::nonNull)
                               .filter(algoOrder -> Objects.equals(algoOrder.getStrategyActionId(), strategyActionId))
                               .findFirst()
                               .orElse(null);
    }

    public boolean hasAlgoOrderByStrategyActionId(Long strategyActionId) {
        return Objects.nonNull(findAlgoOrderByStrategyActionId(strategyActionId));
    }

    public List<Order> safeOrders() {
        if (Objects.nonNull(this.orders)) {
            return this.orders;
        }

        if (Objects.nonNull(this.deal) && Objects.nonNull(this.deal.getOrders())) {
            return this.deal.getOrders();
        }

        return List.of();
    }

    public List<AlgoOrder> safeAlgoOrders() {
        if (Objects.nonNull(this.algoOrders)) {
            return this.algoOrders;
        }

        if (Objects.nonNull(this.deal) && Objects.nonNull(this.deal.getAlgoOrders())) {
            return this.deal.getAlgoOrders();
        }

        return List.of();
    }
}
