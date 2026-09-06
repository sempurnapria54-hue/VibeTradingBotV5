package com.example.marketdata.domain.model;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;

/**
 * Запрос фич на момент решения: привязки операндов к идентичностям
 * вычисления плюс то, что читатель хочет получить сверх значений — цену
 * момента и классифицированную фазу.
 *
 * <p><b>Клаузы и привязки приезжают операндом вызова, а не читаются из
 * чужой базы.</b> market-data потребителем определений стратегий не
 * является и чужую модель определения не разбирает
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»): он получает предикат и считает его на СВОИХ данных — та же
 * форма, что у толерантности свежести.
 *
 * <p>Фаза не персистируется: вычисляется на лету на момент запроса
 * (docs/rules/market-data-retention.md).
 */
@Getter
@Builder
public class FeatureReadRequest {

    /** Привязки индикаторных операндов к идентичностям вычисления. */
    private final List<FeatureBinding> indicatorBindings;

    /** Привязки структурных операндов к идентичностям вычисления. */
    private final List<FeatureBinding> structureBindings;

    /**
     * Авторские клаузы классификации фазы, first-match по позиции в
     * списке; пусто — фаза не спрашивается и в ответ не кладётся.
     */
    private final List<StrategyMarketPhaseRule> phaseRules;

    /**
     * Спрашивает ли читатель цену момента явно — сверх того, что её
     * потребовали бы клаузы фазы.
     *
     * <p>Величина нужна потому, что цену читают и те, у кого клауз фазы
     * нет вовсе: условия шагов сделки и калькуляторы параметров действия
     * (docs/components/models/CalculationContext.md). Вывести потребность
     * из их предикатов market-data не может — предикатов он не получает.
     */
    private final Boolean priceRequired;

    /**
     * Нужна ли цена момента: её назвал читатель либо спрашивает клауза
     * фазы.
     *
     * <p>Вопрос задаётся ДО сбора: цена, в отличие от индикаторов и
     * структур, берётся не из своего хранилища, а чтением у площадки через
     * коннектор. Собирать её там, где её никто не назвал, значит вешать на
     * чтение round-trip наружу и доступность площадки — при том, что ни
     * того, ни другого не нужно.
     */
    public Boolean usesPriceOperand() {
        return isTrue(priceRequired) || phaseClauseReadsPrice();
    }

    /**
     * Спрашивает ли цену хоть одна клауза фазы. Разбор правила живёт у
     * грамматики ({@link StrategyCondition#readsPrice()}), а не здесь:
     * тот же вопрос задаёт и потребитель на своих условиях, и второй его
     * разбор разошёлся бы с первым при расширении каталога операндов.
     */
    private Boolean phaseClauseReadsPrice() {
        return emptyIfNull(phaseRules).stream()
                .map(StrategyMarketPhaseRule::getCondition)
                .filter(Objects::nonNull)
                .anyMatch(condition -> isTrue(condition.readsPrice()));
    }
}
