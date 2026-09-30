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

    /**
     * Авторские имена индикаторов, чьё ПРЕДЫДУЩЕЕ значение читает оценка
     * условия, — вторая половина сравнения, которую гейт покрытия обязан
     * мерить наравне с последней (docs/rules/market-data-freshness.md).
     *
     * <p><b>Прошлое читают пересечение — обоими операндами — и объёмный
     * фильтр — левым;</b> прочие правила его не спрашивают. Прошлое ЦЕНЫ
     * сюда не входит: своего ключа у цены нет, его задаёт индикатор-пара, и
     * перечень у него свой ({@link #pastPriceKeys()}). Перечень — зеркало
     * оценки (docs/components/StrategyConditionEvaluator.md) и живёт у
     * грамматики рядом с {@link #indicatorKeys()}, а не у каждого читателя:
     * новый тип правила, читающий прошлое, пополняет его той же правкой, что
     * заводит его оценку. Совпадение перечня с чтениями оценщика сверяет
     * проба {@code PastReadDeclarationTest} у {@code strategy-engine}.
     */
    public Set<String> pastIndicatorKeys() {
        return keysOf(emptyIfNull(rules).stream()
                        .filter(Objects::nonNull)
                        .flatMap(this::pastOperands),
                StrategyConditionSourceType.INDICATOR, StrategyConditionOperand::getIndicatorKey);
    }

    /**
     * Авторские имена индикаторов-ПАР, по чьему ключу оценка условия читает
     * ПРЕДЫДУЩУЮ ЦЕНУ, — ценовая половина прошлого, которую гейт покрытия
     * обязан мерить наравне с индикаторной (docs/rules/market-data-freshness.md).
     *
     * <p><b>Ключ — индикатор-пара, а не цена.</b> У цены своего таймфрейма
     * нет, и её прошлое — закрытие свечи, на которой посчитано предыдущее
     * значение индикатора по другую сторону пересечения; раскладка
     * предыдущих цен ключуется его именем
     * (docs/components/StrategyConditionEvaluator.md §«Вторая половина
     * времени»). Читает её только пересечение, у которого одна сторона —
     * цена, а другая — индикатор: у пары-не-индикатора прошлого цены нет, и
     * операнд недоступен без чтения раскладки. Объёмный фильтр прошлое цены
     * не читает — у цены он его не спрашивает вовсе.
     *
     * <p>Та же дисциплина, что у {@link #pastIndicatorKeys()}: перечень —
     * зеркало оценки и живёт у грамматики; совпадение с чтениями оценщика
     * сверяет та же проба {@code PastReadDeclarationTest}.
     */
    public Set<String> pastPriceKeys() {
        return keysOf(emptyIfNull(rules).stream()
                        .filter(Objects::nonNull)
                        .flatMap(this::pricePairOperands),
                StrategyConditionSourceType.INDICATOR, StrategyConditionOperand::getIndicatorKey);
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
        return keysOf(emptyIfNull(rules).stream()
                        .flatMap(rule -> Stream.of(rule.getLeftOperand(), rule.getRightOperand())),
                sourceType, accessor);
    }

    /** Непустые имена операндов данного типа источника; отбор по типу, затем своё поле. */
    private Set<String> keysOf(Stream<StrategyConditionOperand> operands,
                               StrategyConditionSourceType sourceType,
                               Function<StrategyConditionOperand, String> accessor) {
        return operands
                .filter(Objects::nonNull)
                .filter(operand -> Objects.equals(sourceType, operand.getSourceType()))
                .map(accessor)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /** Операнды правила, чьё предыдущее значение читает его оценка; пусто — прошлое не читается. */
    private Stream<StrategyConditionOperand> pastOperands(StrategyConditionRule rule) {
        if (Objects.equals(StrategyConditionRuleType.CROSSOVER, rule.getRuleType())) {
            return Stream.of(rule.getLeftOperand(), rule.getRightOperand());
        }
        if (Objects.equals(StrategyConditionRuleType.VOLUME_FILTER_PASSED, rule.getRuleType())) {
            return Stream.of(rule.getLeftOperand());
        }
        return Stream.empty();
    }

    /**
     * Операнды-пары ценовых сторон пересечения: по их ключу оценка читает
     * прошлое цены; пусто — правило его не читает. Пара, не являющаяся
     * индикатором, отсеивается отбором по типу источника.
     */
    private Stream<StrategyConditionOperand> pricePairOperands(StrategyConditionRule rule) {
        if (isFalse(Objects.equals(StrategyConditionRuleType.CROSSOVER, rule.getRuleType()))) {
            return Stream.empty();
        }
        return Stream.of(pairOfPrice(rule.getLeftOperand(), rule.getRightOperand()),
                pairOfPrice(rule.getRightOperand(), rule.getLeftOperand()));
    }

    /** Пара операнда, если сам операнд — цена; иначе пусто. */
    private StrategyConditionOperand pairOfPrice(StrategyConditionOperand operand, StrategyConditionOperand pair) {
        return isTrue(isPriceOperand(operand)) ? pair : null;
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
