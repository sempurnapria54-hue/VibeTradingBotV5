package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Общее условие применимости шага стратегии: все rules должны быть
 * истинны; проверяются по level ASC (локальный порядок внутри
 * условия, не глобальный порядок шагов). Персистится целиком
 * JSONB-полем condition на строке strategy_step; вычисление
 * истинности — деталь evaluator'а (downstream,
 * StrategyConditionEvaluator). См.
 * docs/models/domain/aggregate/Strategy.md (§Условия),
 * docs/rules/strategy-condition-contract.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyCondition {

    /** Правила условия; шаг применим, когда истинны все. */
    private List<StrategyConditionRule> rules;

    /**
     * Спрашивает ли условие цену момента.
     *
     * <p><b>Вопрос задаётся до сбора данных.</b> Цена, в отличие от
     * индикаторов и структуры, не лежит в хранилище рыночных данных: её
     * читают у площадки. Снимать её для условий, которые её не называют,
     * значит вешать на каждую оценку round-trip наружу и доступность
     * площадки; отсюда предикат — и он живёт здесь, у грамматики, а не у
     * каждого, кто данные собирает: собирающих трое (классификация фазы,
     * контекст оценки условий, контекст расчёта), а ответ у них один.
     */
    public Boolean readsPrice() {
        return emptyIfNull(rules).stream().anyMatch(this::ruleReadsPrice);
    }

    /**
     * Спрашивает ли условие фазу рынка. Отдельный вопрос от
     * {@link #readsPrice()}: фаза приезжает связкой фич, а цена — чтением
     * у площадки, и пустоты у них наступают по разным причинам.
     */
    public Boolean readsMarketPhase() {
        return namesSource(StrategyConditionSourceType.MARKET_PHASE);
    }

    /**
     * Авторские имена операндов-индикаторов, названные условием.
     *
     * <p><b>Имя, а не идентичность вычисления.</b> Раскладки фич момента
     * ключуются тем же именем, которым оперирует автор стратегии
     * (docs/components/models/CalculationContext.md), и совпадение имён —
     * единственное, чем читатель проверяет доступность операнда.
     */
    public Set<String> indicatorKeys() {
        return operandKeys(StrategyConditionSourceType.INDICATOR, StrategyConditionOperand::getIndicatorKey);
    }

    /** Авторские имена операндов-структур рынка, названные условием. */
    public Set<String> structureKeys() {
        return operandKeys(StrategyConditionSourceType.MARKET_STRUCTURE, StrategyConditionOperand::getStructureKey);
    }

    /**
     * Опирается ли условие на рыночные данные — то есть подлежит ли шаг
     * гейту свежести (docs/rules/market-data-freshness.md, род действия
     * {@code DATA_DEPENDENT}).
     *
     * <p>Условие, читающее только факты сделки (позиция, заявки, пороги
     * хода), рыночных данных не спрашивает, и устаревание владельца его
     * не касается: гейт, поставленный на него, останавливал бы выход ровно
     * тогда, когда данные пропали.
     */
    public Boolean readsMarketData() {
        return isTrue(readsPrice())
                || isTrue(readsMarketPhase())
                || isFalse(indicatorKeys().isEmpty())
                || isFalse(structureKeys().isEmpty());
    }

    private Set<String> operandKeys(StrategyConditionSourceType sourceType,
                                    Function<StrategyConditionOperand, String> accessor) {
        return emptyIfNull(rules).stream()
                .flatMap(rule -> Stream.of(rule.getLeftOperand(), rule.getRightOperand()))
                .filter(Objects::nonNull)
                .filter(operand -> Objects.equals(sourceType, operand.getSourceType()))
                .map(accessor)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private Boolean namesSource(StrategyConditionSourceType sourceType) {
        return emptyIfNull(rules).stream()
                .flatMap(rule -> Stream.of(rule.getLeftOperand(), rule.getRightOperand()))
                .filter(Objects::nonNull)
                .anyMatch(operand -> Objects.equals(sourceType, operand.getSourceType()));
    }

    private Boolean ruleReadsPrice(StrategyConditionRule rule) {
        return isPriceOperand(rule.getLeftOperand()) || isPriceOperand(rule.getRightOperand());
    }

    private Boolean isPriceOperand(StrategyConditionOperand operand) {
        return nonNull(operand)
                && Objects.equals(operand.getSourceType(), StrategyConditionSourceType.PRICE);
    }
}
