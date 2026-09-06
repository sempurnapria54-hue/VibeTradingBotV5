package com.example.tradingcore.domain.market;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Фичи одного момента решения, снятые у владельца рыночных данных.
 * Runtime-объект прохода, не хранится.
 *
 * <p><b>Раскладки ключуются авторскими именами операндов</b> — теми, что
 * объявила стратегия. Идентичность вычисления есть номенклатура владельца
 * данных, и держать её здесь значило бы заставлять каждого читателя
 * переводить ответ обратно в имена, которыми он же и спрашивал.
 *
 * <p><b>Пустое место в раскладке означает «вход недоступен».</b>
 * Отсутствующее и устаревшее ключа не занимают вовсе, и предикат на таком
 * операнде консервативно ложен
 * (docs/rules/absent-value-semantics.md).
 */
@Value
@Builder
public class MarketFeatures {

    /** Последние свежие значения индикаторов по авторскому имени операнда. */
    Map<String, IndicatorValue> latestIndicators;

    /** Предыдущие значения тех же идентичностей — вторая половина сравнений. */
    Map<String, IndicatorValue> previousIndicators;

    /** Последние свежие структуры рынка по авторскому имени операнда. */
    Map<String, MarketStructure> structures;

    /** Цены момента; пусто — не спрашивались либо площадка отказала. */
    MarketPriceData marketPriceData;

    /** Фаза рынка по клаузам стратегии; пусто — клауз у неё нет. */
    MarketPhase marketPhase;

    /** Тип фазы либо пусто — операнд предикатов фазы. */
    public MarketPhase.Type phaseType() {
        return isNull(marketPhase) ? null : marketPhase.getType();
    }

    /**
     * Рыночная половина контекста оценки условий — значения, их
     * предыдущие значения, структуры, цена и фаза момента.
     *
     * <p><b>Собирается здесь, потому что читателей у неё двое:</b> проход
     * сделки достраивает её фактами сделки, отбор входа берёт как есть —
     * фактов у него нет вовсе. Второй сборщик разошёлся бы с первым
     * ровно при вводе нового рыночного операнда грамматики: половина
     * читателей оценивала бы условия на неполном контексте, и молча.
     *
     * <p>Время оценки здесь не ставится: оно точка отсчёта
     * {@code TIME}-операндов, то есть свойство момента РЕШЕНИЯ, и ставит
     * его читатель.
     */
    public ConditionEvaluationContext.ConditionEvaluationContextBuilder conditionOperands() {
        return ConditionEvaluationContext.builder()
                .latestIndicators(emptyIfNull(latestIndicators))
                .previousIndicators(emptyIfNull(previousIndicators))
                .structures(emptyIfNull(structures))
                .price(lastPrice())
                .marketPhase(phaseType());
    }

    /**
     * Та же половина у читателя, которому фич не снимали вовсе, — все
     * рыночные операнды пусты, и предикаты на них консервативно ложны
     * (docs/rules/absent-value-semantics.md).
     */
    public static ConditionEvaluationContext.ConditionEvaluationContextBuilder absentConditionOperands() {
        return ConditionEvaluationContext.builder()
                .latestIndicators(Map.of())
                .previousIndicators(Map.of())
                .structures(Map.of());
    }

    /** Последняя цена сделки; пусто — цену не спрашивали либо площадка отказала. */
    private BigDecimal lastPrice() {
        return isNull(marketPriceData) ? null : marketPriceData.getExternalLastPrice();
    }

    /**
     * Все рыночные операнды, названные условием, доступны в этой связке.
     *
     * <p><b>Это и есть гейт свежести у читателя.</b> Владелец данных
     * отдаёт только свежее по сроку спрашивающей настройки, поэтому
     * отсутствующий ключ и есть ответ «данным доверять нельзя»:
     * различать «устарело» и «нет» читателю не нужно
     * (docs/spec/market-data-freshness.json, величина
     * {@code freshnessState} — оба состояния ведут к одной реакции).
     *
     * <p>Своего носителя свежести у ядра поэтому не заводится: он был бы
     * вторым ответом на вопрос, на который уже ответил владелец.
     */
    public Boolean covers(StrategyCondition condition) {
        if (isNull(condition)) {
            return true;
        }
        if (isTrue(condition.readsPrice()) && isNull(marketPriceData)) {
            return false;
        }
        if (isTrue(condition.readsMarketPhase()) && isNull(phaseType())) {
            return false;
        }
        return emptyIfNull(latestIndicators).keySet().containsAll(condition.indicatorKeys())
                && emptyIfNull(structures).keySet().containsAll(condition.structureKeys());
    }
}
