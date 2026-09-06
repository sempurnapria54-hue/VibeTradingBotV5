package com.example.strategy.engine.condition;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Готовые данные для проверки StrategyCondition: значения индикаторов и
 * структуры по ключам настроек (latest + previous для slope/crossover),
 * текущая цена, время оценки и — в контексте сделки — её факты.
 * Собирается потребителем из готовых результатов; evaluator по свечам
 * ничего не считает. Runtime-объект.
 * См. docs/components/StrategyConditionEvaluator.md.
 *
 * <p><b>Ключи здесь — авторские имена операндов, а не идентичности
 * вычисления.</b> Контекст собирает тот, кто держит клаузы: он и знает,
 * какое имя какой идентичности соответствует. Библиотека об идентичностях
 * market-data не знает — иначе интерпретатор грамматики стал бы читателем
 * чужого хранилища.
 *
 * <p><b>Факты сделки опциональны, и их пустота — это и есть whitelist
 * контекста.</b> Классификация фазы рынка собирает контекст без них, и
 * правила, читающие эпизод, транш и фазу входа, оказываются на пустом
 * операнде — то есть консервативно ложны. Отдельного перечня разрешённых
 * типов правил заводить не нужно: он был бы вторым носителем того же
 * различения (docs/spec/deal-condition.json).
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

    /** Время оценки (UTC) — точка отсчёта TIME-операндов. */
    private final OffsetDateTime evaluationTime;

    /**
     * Фаза рынка этого прохода — вычисленное значение, не загруженная
     * сущность. {@code UNKNOWN} знанием не является: он производится и
     * недоступностью операнда, и несрабатыванием всех клауз
     * (docs/spec/market-phase-condition.json).
     */
    private final MarketPhase.Type marketPhase;

    /**
     * Фаза рынка, наблюдённая при ВХОДЕ сделки. Темпоральный операнд смены
     * тренда взят у сделки, а не у истории фазы: своей истории фаза не
     * имеет и не персистируется. Пусто у восстановленной сделки — входа по
     * объявлению у неё не было.
     */
    private final MarketPhase.Type entryMarketPhase;

    /** Направление сделки — знак хода цены от цены входа. */
    private final StrategyTradeDirection direction;

    /** Живой эпизод позиции; null — эпизода нет. */
    private final Position activePosition;

    /** Транш, чей шаг оценивается; null — оценка вне транша. */
    private final DealTranche tranche;
}
