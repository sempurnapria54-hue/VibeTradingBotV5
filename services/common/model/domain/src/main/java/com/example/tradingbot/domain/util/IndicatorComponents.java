package com.example.tradingbot.domain.util;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.condition.IndicatorComponent;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Единый справочник адресуемых компонентов по типу индикатора: какие
 * {@link IndicatorComponent} допустимы для какого {@link IndicatorValue.Type}
 * и какие типы многокомпонентны (компонент обязателен). Опора и
 * create-валидации (контракт авторинга), и evaluator'а (выбор компонента).
 * Утилитный класс — в пакете util (конвенция проекта).
 *
 * <p><b>Пустой тип читается отсутствием, а не отказом</b>
 * (docs/rules/absent-value-semantics.md): компонентов у него нет, и
 * многокомпонентным он не является. Охрана явная — неизменяемая карта JDK
 * хеширует ключ до всякой проверки, и умолчание {@code getOrDefault} на
 * пустом ключе не срабатывает.
 */
@UtilityClass
public class IndicatorComponents {

    private static final Map<IndicatorValue.Type, Set<IndicatorComponent>> ALLOWED = Map.of(
            IndicatorValue.Type.MACD, Set.of(
                    IndicatorComponent.MACD_LINE, IndicatorComponent.SIGNAL_LINE, IndicatorComponent.HISTOGRAM),
            IndicatorValue.Type.STOCHASTIC, Set.of(
                    IndicatorComponent.STOCH_K, IndicatorComponent.STOCH_D),
            IndicatorValue.Type.BOLLINGER_BANDS, Set.of(
                    IndicatorComponent.UPPER_BAND, IndicatorComponent.MIDDLE_BAND, IndicatorComponent.LOWER_BAND,
                    IndicatorComponent.BANDWIDTH, IndicatorComponent.PERCENT_B));

    /** Допустимые компоненты типа индикатора (пусто — одно-компонентный, компонент не задаётся). */
    public static Set<IndicatorComponent> allowedFor(IndicatorValue.Type type) {
        if (isNull(type)) {
            return Set.of();
        }
        return ALLOWED.getOrDefault(type, Set.of());
    }

    /** Тип многокомпонентный — адресный компонент операнда обязателен. */
    public static Boolean isMultiComponent(IndicatorValue.Type type) {
        return nonNull(type) && ALLOWED.containsKey(type);
    }
}
