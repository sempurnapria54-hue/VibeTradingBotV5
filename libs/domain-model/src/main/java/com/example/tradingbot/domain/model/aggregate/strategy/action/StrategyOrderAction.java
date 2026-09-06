package com.example.tradingbot.domain.model.aggregate.strategy.action;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ожидаемое действие над ordinary order (вход / ремодел / отмена).
 * Доменное намерение positionReducingOnly остаётся в strategy-layer;
 * OKX reduceOnly — только client/adapter-поле. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyOrderAction).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyOrderAction extends Auditable implements StrategyAction {

    /** Технический ID действия. */
    private Long id;

    /** Стабильный ключ действия в рамках StrategyDetail. */
    private String key;

    /** Ключ target-действия для REPLACE/CANCEL; для CREATE null. */
    private String targetActionKey;

    /** Тип действия: CREATE/REPLACE/CANCEL. */
    private StrategyActionType actionType;

    /** Бизнес-тип ордера: ENTRY / ENTRY_ATTACHED_STOP_LOSS. */
    private Order.Type orderType;

    /** Нормализованное торговое направление. */
    private StrategyTradeDirection direction;

    /** Доля расчётного объёма, % (объём считает SizeCalculator). */
    private BigDecimal allocationPercents;

    /** Намерение reduce-only: ордер только уменьшает позицию. */
    private Boolean positionReducingOnly;

    /** Правило расчёта цены размещения; для market-like входа null. */
    private StrategyPricePlacement placement;

    /** Attached-защита (для ENTRY_ATTACHED_STOP_LOSS обязательна, для ENTRY null). */
    private StrategyAttachedProtectionSettings attachedProtection;

    /**
     * Сторона заявки — направление действия плюс намерение reduce-only.
     *
     * <p><b>Сторона не совпадает с направлением сделки</b>, и это не
     * оговорка: закрывающая заявка на длинной ноге имеет сторону
     * {@code SELL} (docs/models/domain/core/Order.md). Вывод живёт здесь,
     * у объявления, потому что оба операнда объявлены им же.
     */
    public Order.Side side() {
        boolean longSide = StrategyTradeDirection.LONG.equals(direction);
        boolean reducing = Boolean.TRUE.equals(positionReducingOnly);
        return longSide == reducing ? Order.Side.SELL : Order.Side.BUY;
    }

    /**
     * У заявки уровень задаёт только встроенная защита, а она объявляется
     * блоком настроек стопа — наблюдаемого уровня у неё не бывает.
     */
    @Override
    public StrategyLevelSource levelSource() {
        return StrategyLevelSource.DECLARED;
    }
}
