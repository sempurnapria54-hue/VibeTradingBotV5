package com.example.tradingbot.domain.model.strategy;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.algo_order.ConditionType;
import com.example.tradingbot.domain.model.market.MarketPhase;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.order.Order;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Getter
@Setter
public class StrategyDetails extends Auditable {

    private Long id;

    /**
     * Идентификатор стратегии-владельца.
     */
    private Long strategyId;

    /**
     * Фаза рынка, для которой предназначены эти настройки.
     */
    private MarketPhase.Type marketPhaseType;

    /**
     * Текущий статус набора настроек.
     */
    private Status status;

    /**
     * Политика входа для данной фазы рынка.
     */
    private EntryPolicy entryPolicy;

    /**
     * Тип обычного ордера, который будет создан на входе.
     */
    private Order.Type entryOrderType;

    /**
     * Тип attached-защиты, которая должна быть создана вместе с entry-ордером.
     */
    private AttachedAlgoOrder.Type attachedProtectionType;

    /**
     * Тип основной защиты, которая будет создана после переключения с attached-защиты.
     */
    private ConditionType mainProtectionType;

    /**
     * Риск на сделку в процентах от доступного капитала.
     * <p>
     * Пример:
     * 1.0 = 1%
     */
    private BigDecimal riskPerTradePercent;

    /**
     * Максимально допустимое плечо для этой фазы рынка.
     * <p>
     * Важно:
     * - это не фактическое плечо сделки;
     * - это верхняя граница, которую нельзя превышать.
     * <p>
     * Фактическое плечо должно рассчитываться отдельно
     * на основании параметров сделки и ограничиваться этим значением.
     */
    private Integer maxLeverage;

    /**
     * Плановое отношение profit/risk.
     * <p>
     * Это целевой ориентир стратегии:
     * - для расчётной цели сделки,
     * - для оценки качества сигнала,
     * - для подбора сопровождения позиции.
     * <p>
     * Важно:
     * - само по себе это значение не должно напрямую определять плечо;
     * - реальное плечо лучше рассчитывать из риска, цены входа, SL и объёма позиции.
     */
    private BigDecimal targetRiskRewardRatio;

    /**
     * Базовое значение initial Stop-Loss.
     * <p>
     * Интерпретация зависит от stopLossType:
     * - PERCENT -> процент от цены входа;
     * - ATR -> множитель ATR;
     * - MARKET_STRUCTURE -> буфер относительно рыночной структуры.
     */
    private BigDecimal stopLossValue;

    /**
     * Базовое значение trailing-параметра.
     * <p>
     * Интерпретация зависит от mainProtectionType
     * и/или отдельной логики сопровождения.
     */
    private BigDecimal trailingValue;

    /**
     * Тип initial Stop-Loss.
     */
    private StopLossType stopLossType;

    /**
     * Текущий статус деталей стратегии.
     */
    public enum Status {

        /**
         * Черновик настроек.
         * <p>
         * Такой набор параметров ещё не готов к использованию:
         * - может быть неполным,
         * - может редактироваться,
         * - не должен участвовать в live-торговле.
         */
        DRAFT,

        /**
         * Активные настройки.
         * <p>
         * Именно такие детали стратегии должны применяться,
         * если:
         * - стратегия активна,
         * - текущая фаза рынка совпадает с marketPhaseType.
         */
        ACTIVE,

        /**
         * Временно отключённые настройки.
         * <p>
         * Используется, когда:
         * - стратегия в целом существует,
         * - но торговлю в этой фазе рынка нужно запретить,
         * не удаляя сам набор параметров.
         */
        DISABLED,

        /**
         * Архивная версия настроек.
         * <p>
         * Нужна для:
         * - истории изменений,
         * - воспроизводимости,
         * - анализа старых запусков и бэктестов.
         * <p>
         * В live-торговле использоваться не должна.
         */
        ARCHIVED
    }

    /**
     * Политика входа для данной фазы рынка.
     * <p>
     * Этот enum отвечает не за "направление позиции" напрямую,
     * а за то, как стратегия должна интерпретировать текущую фазу рынка.
     */
    public enum EntryPolicy {

        /**
         * Следовать направлению текущей фазы рынка.
         * <p>
         * Примеры:
         * - BULL_TREND -> приоритет long-входов;
         * - BEAR_TREND -> приоритет short-входов.
         */
        FOLLOW_PHASE,

        /**
         * Торговать против доминирующей фазы рынка.
         * <p>
         * Используется для контртрендовых сценариев
         * и требует более осторожной настройки риска.
         */
        CONTRARIAN,

        /**
         * Вход в сделку в данной фазе рынка запрещён.
         * <p>
         * Пример:
         * - стратегия не торгует во флэте;
         * - стратегия не торгует в неопределённой фазе рынка.
         */
        NO_TRADE
    }

    /**
     * Тип initial Stop-Loss.
     * <p>
     * Этот enum определяет,
     * по какой логике будет вычисляться стартовый защитный стоп.
     */
    public enum StopLossType {

        /**
         * Stop-Loss рассчитывается как процент от цены входа.
         * <p>
         * Пример:
         * - stopLossValue = 1.5
         * - значит SL = 1.5% от цены входа.
         */
        PERCENT,

        /**
         * Stop-Loss рассчитывается на основе ATR.
         * <p>
         * Пример:
         * - stopLossValue = 1.5
         * - значит SL = 1.5 * ATR.
         */
        ATR,

        /**
         * Stop-Loss рассчитывается по рыночной структуре.
         * <p>
         * Примеры:
         * - ниже локального минимума,
         * - выше локального максимума,
         * - за swing high / swing low.
         */
        MARKET_STRUCTURE
    }

    /**
     * Разрешена ли торговля по этим деталям стратегии.
     * <p>
     * Торговля считается разрешённой только если:
     * - детали стратегии активны;
     * - политика входа не запрещает вход.
     */
    public boolean isTradingEnabled() {
        return isFalse(isTradingDisabled());
    }

    /**
     * Запрещена ли торговля по этим деталям стратегии.
     * <p>
     * Торговля считается запрещённой, если:
     * - детали стратегии не в статусе ACTIVE;
     * - либо политика входа = NO_TRADE.
     */
    public boolean isTradingDisabled() {
        if (isNotActive()) {
            return true;
        }

        if (isNull(entryPolicy)) {
            return true;
        }

        return entryPolicy == EntryPolicy.NO_TRADE;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isNotActive() {
        return isFalse(isActive());
    }
}