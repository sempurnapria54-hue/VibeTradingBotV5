package com.example.tradingbot.domain.service.market.condition;

import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Готовые данные для проверки StrategyCondition в контексте классификации
 * фазы: значения индикаторов и структуры по ключам настроек (latest + при
 * необходимости previous для slope/crossover), текущая цена (опционально) и
 * время оценки. Собирается потребителем (MarketPhaseJob) из готовых
 * результатов; evaluator по свечам ничего не считает. Runtime-объект
 * (deal-контекст — Position/Order-факты — добавится с кластером сделки).
 * См. docs/components/StrategyConditionEvaluator.md.
 */
@Getter
@Builder
public class ConditionEvaluationContext {

    /** Последние значения индикаторов по ключу настройки (indicatorKey). */
    private final Map<String, IndicatorValue> latestIndicators;

    /** Предыдущие значения индикаторов по ключу (для slope/crossover); может быть пустым. */
    private final Map<String, IndicatorValue> previousIndicators;

    /** Последние структуры рынка по ключу настройки (structureKey). */
    private final Map<String, MarketStructure> structures;

    /** Текущая рыночная цена для PRICE-операндов; null — недоступна. */
    private final BigDecimal price;

    /** Время бара оценки (UTC) — точка отсчёта производного confirmedAt и TIME-операндов. */
    private final OffsetDateTime evaluationTime;
}
