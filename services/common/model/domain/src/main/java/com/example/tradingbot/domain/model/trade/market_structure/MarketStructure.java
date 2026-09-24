package com.example.tradingbot.domain.model.trade.market_structure;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Результат расчёта структуры рынка по закрытым свечам.
 *
 * <p><b>Ключуется идентичностью вычисления</b>
 * ({@code marketStructureConfigId}): таймфрейм, канонические параметры и
 * ключи входов резолвера. Довод общий с индикатором —
 * {@code docs/models/domain/other/IndicatorValue.md} §«Ключевание —
 * идентичностью вычисления».
 *
 * <p><b>Ключи входов входят в идентичность:</b> два вычисления с разными
 * входами дают разные строки, иначе последнее записанное затирало бы
 * чужое.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketStructure extends Auditable {

    /** Технический ID результата расчёта. */
    private Long id;

    /** Внутренний ID инструмента. */
    private Long instrumentId;

    /** Идентичность вычисления: таймфрейм, параметры окна, ключи входов резолвера. */
    private Long marketStructureConfigId;

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

    /** Есть подтверждённый пробой в окне расчёта. */
    public Boolean hasConfirmedBreakout() {
        return nonNull(breakoutEvent);
    }

    /**
     * Подтверждённый пробой в окне идёт в заданном направлении. Пробой
     * вниз входу в лонг «на подтверждённом пробое» не ответ: без
     * направления условие открывало бы сделку и на сломе против неё
     * (docs/models/domain/other/MarketStructure.md §«MarketBreakoutEvent
     * (раздел)»). Пустое направление у любой стороны — ложь.
     */
    public Boolean hasConfirmedBreakout(MarketBreakoutEvent.Direction direction) {
        return isTrue(hasConfirmedBreakout())
                && nonNull(direction)
                && Objects.equals(breakoutEvent.getDirection(), direction);
    }

    /**
     * Последний подтверждённый уровень заданного типа или null (резолв
     * уровня для placement/SL). Свингов одного типа в окне несколько, и
     * стоп либо размещение строятся от <b>последнего</b> из них — от
     * структуры, которую сломает цена, а не от самого старого пивота окна
     * (docs/models/domain/other/MarketStructure.md §«MarketPriceLevel
     * (раздел)»). Известный момент подтверждения бьёт неизвестный; при
     * равных моментах либо двух неизвестных побеждает стоящий в перечне
     * позже.
     */
    public MarketPriceLevel findLevel(MarketPriceLevel.Type levelType) {
        if (isEmpty(levels) || isNull(levelType)) {
            return null;
        }
        return levels.stream()
                .filter(level -> Objects.equals(level.getType(), levelType))
                .reduce((kept, next) -> isTrue(kept.isSupersededBy(next)) ? next : kept)
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
