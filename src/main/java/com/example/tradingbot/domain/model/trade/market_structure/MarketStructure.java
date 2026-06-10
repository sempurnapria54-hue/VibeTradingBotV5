package com.example.tradingbot.domain.model.trade.market_structure;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.Auditable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Готовый результат расчёта структуры рынка (уровни, диапазоны, тренд),
 * рассчитанный MarketStructureJob (вычисление делегируется
 * MarketStructureResolver) по закрытым свечам для конкретной
 * настройки-владельца StrategyMarketStructureSetting. Ключуется
 * настройкой-владельцем (strategyMarketStructureSettingId), не шарится и не
 * ключуется по идентичности конфигурации — реестр убран (трек D). Тип
 * структуры — выход расчёта, не вход настройки. Persisted-модель
 * рыночных данных. См. docs/models/domain/other/MarketStructure.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketStructure extends Auditable {

    /** Технический ID результата расчёта. */
    private Long id;

    /** Внутренний ID инструмента. */
    private Long instrumentId;

    /** FK на настройку-владельца StrategyMarketStructureSetting (owner-ключевание). */
    private Long strategyMarketStructureSettingId;

    /** Тип структуры рынка (выход расчёта). */
    private Type type;

    /** Начало окна свечей расчёта. */
    private OffsetDateTime windowStartAt;

    /** Конец окна свечей расчёта (точка отсчёта свежести). */
    private OffsetDateTime windowEndAt;

    /** Свеча, на которой структура подтверждена (гейт «использовать без look-ahead»). */
    private OffsetDateTime confirmedAt;

    /** Ценовые уровни структуры. */
    private List<MarketPriceLevel> levels;

    /**
     * Предвычисленное событие подтверждённого пробоя (сломанный уровень +
     * направление + confirmedAt), которое условие RANGE_BREAKOUT_CONFIRMED
     * читает готовым; null — подтверждённого пробоя в окне нет. Форма
     * события — деталь реализации (CODE), детекция — на стороне резолвера.
     */
    private MarketBreakoutEvent breakoutEvent;

    /** Структура — трендовая (восходящая или нисходящая). */
    public Boolean isTrend() {
        return Objects.equals(type, Type.UPTREND) || Objects.equals(type, Type.DOWNTREND);
    }

    /** Есть подтверждённый пробой в окне расчёта. */
    public Boolean hasConfirmedBreakout() {
        return Objects.nonNull(breakoutEvent);
    }

    /** Первый уровень заданного типа или null (резолв уровня для placement/SL). */
    public MarketPriceLevel findLevel(MarketPriceLevel.Type levelType) {
        if (isEmpty(levels) || isNull(levelType)) {
            return null;
        }
        return levels.stream()
                .filter(level -> Objects.equals(level.getType(), levelType))
                .findFirst()
                .orElse(null);
    }

    /** Тип структуры рынка. */
    public enum Type {

        /** Диапазон (боковик с границами). */
        RANGE,

        /** Восходящий тренд. */
        UPTREND,

        /** Нисходящий тренд. */
        DOWNTREND,

        /** Структура не определена (консервативный дефолт). */
        UNKNOWN
    }
}
