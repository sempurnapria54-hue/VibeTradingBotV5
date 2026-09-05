package com.example.marketdata.domain.model;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;

/**
 * Запрос классификации фазы рынка: клаузы потребителя плюс привязки их
 * операндов к идентичностям вычисления.
 *
 * <p><b>Клаузы приезжают операндом вызова, а не читаются из чужой
 * базы.</b> market-data потребителем определений стратегий не является и
 * чужую модель определения не разбирает
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»): он получает предикат и считает его на СВОИХ данных — та же
 * форма, что у толерантности свежести.
 *
 * <p>Фаза не персистируется: вычисляется на лету на момент запроса
 * (docs/rules/market-data-retention.md).
 */
@Getter
@Builder
public class MarketPhaseRequest {

    /** Авторские клаузы классификации, first-match по позиции в списке. */
    private final List<StrategyMarketPhaseRule> phaseRules;

    /** Привязки индикаторных операндов к идентичностям вычисления. */
    private final List<FeatureBinding> indicatorBindings;

    /** Привязки структурных операндов к идентичностям вычисления. */
    private final List<FeatureBinding> structureBindings;

    /**
     * Спрашивает ли хоть одна клауза цену момента.
     *
     * <p>Вопрос задаётся ДО сбора контекста: цена, в отличие от
     * индикаторов и структур, берётся не из своего хранилища, а чтением у
     * площадки через коннектор. Собирать её для клауз, которые её не
     * называют, значит вешать на классификацию фазы round-trip наружу и
     * доступность площадки — там, где ни того, ни другого не нужно.
     */
    public Boolean usesPriceOperand() {
        return emptyIfNull(phaseRules).stream()
                .map(StrategyMarketPhaseRule::getCondition)
                .filter(Objects::nonNull)
                .flatMap(condition -> emptyIfNull(condition.getRules()).stream())
                .anyMatch(this::readsPrice);
    }

    private Boolean readsPrice(StrategyConditionRule rule) {
        return isPriceOperand(rule.getLeftOperand()) || isPriceOperand(rule.getRightOperand());
    }

    private Boolean isPriceOperand(StrategyConditionOperand operand) {
        return nonNull(operand)
                && Objects.equals(operand.getSourceType(), StrategyConditionSourceType.PRICE);
    }
}
