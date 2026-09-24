package com.example.strategies.mapping;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.message.StrategyActivatedMessage;
import com.example.tradingbot.message.StrategyLifecycleMessage;
import java.util.List;
import java.util.Map;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

/**
 * Маппинг domain → message для классов событий владельца определений
 * (docs/architecture/contracts.md §События).
 *
 * <p><b>Форму провода строит граница, а не домен</b>
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»): прежде
 * содержимое собиралось конструктором прямо в сервисе жизненного цикла.
 *
 * <p><b>Непокрытое целевое поле здесь ОШИБКА</b>, а не умолчание: пустое
 * поле формы сообщения есть потерянный факт у читателя на другой стороне
 * провода, и замечает он его в проде, а не при сборке.
 *
 * <p><b>Снимок определения едет доменным деревом, а не зеркалом.</b>
 * Второй носитель той же формы разошёлся бы с первым при первом
 * расширении каталога условий (.claude/rules/carrier-levels.md); поэтому
 * поле {@code definition} несёт тот же доменный тип, а не форму по полям.
 *
 * <p><b>Но едет КОПИЯ дерева без числовых ключей базы</b> ни у одного
 * узла: они границу сервиса не пересекают
 * (docs/architecture/data-ownership.md §Идентификаторы). Идентичность на
 * проводе — {@code internalId} определения и внешних ссылок, узел дерева
 * опознаётся положением в нём. Копия, а не очистка на месте: вызывающий
 * держит то же дерево и после записи факта.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StrategyEventMessageMapper {

    /**
     * Активация: три идентичности радиуса рядом со снимком.
     *
     * <p>Они едут отдельными полями <b>намеренно</b> — читатель журнала
     * достаёт из содержимого только одноимённые компоненты верхнего
     * уровня, а внутри снимка определение зовётся {@code internalId}
     * (docs/architecture/contracts.md §«Производность меряется у ЧИТАТЕЛЯ,
     * а не у формы»).
     */
    @Mapping(target = "strategyInternalId", source = "definition.internalId")
    @Mapping(target = "exchangeAccountInternalId", source = "definition.exchangeAccountInternalId")
    @Mapping(target = "instrumentInternalId", source = "definition.instrumentInternalId")
    @Mapping(target = "definition", source = "definition", qualifiedByName = "definitionWithoutKeys")
    StrategyActivatedMessage domainToActivatedMessage(Strategy definition, String actor);

    /** Прочие переходы: дерево не едет — копия у потребителя уже есть. */
    @Mapping(target = "strategyInternalId", source = "definition.internalId")
    @Mapping(target = "exchangeAccountInternalId", source = "definition.exchangeAccountInternalId")
    @Mapping(target = "instrumentInternalId", source = "definition.instrumentInternalId")
    StrategyLifecycleMessage domainToLifecycleMessage(Strategy definition, String actor);

    // ===== снимок определения без ключей базы =====

    /** Корень снимка: ключ определения не едет. */
    @Named("definitionWithoutKeys")
    @Mapping(target = "id", ignore = true)
    Strategy domainToMessageDefinition(Strategy definition);

    @Mapping(target = "id", ignore = true)
    StrategyMarketPhaseSetting domainToMessageDefinition(StrategyMarketPhaseSetting setting);

    @Mapping(target = "id", ignore = true)
    StrategyIndicatorSetting domainToMessageDefinition(StrategyIndicatorSetting setting);

    @Mapping(target = "id", ignore = true)
    StrategyMarketStructureSetting domainToMessageDefinition(StrategyMarketStructureSetting setting);

    @Mapping(target = "id", ignore = true)
    StrategyDetail domainToMessageDefinition(StrategyDetail detail);

    @Mapping(target = "id", ignore = true)
    StrategyTranche domainToMessageDefinition(StrategyTranche tranche);

    @Mapping(target = "id", ignore = true)
    StrategyStep domainToMessageDefinition(StrategyStep step);

    @BeanMapping(subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
    @SubclassMapping(source = StrategyOrderAction.class, target = StrategyOrderAction.class)
    @SubclassMapping(source = StrategyAlgoOrderAction.class, target = StrategyAlgoOrderAction.class)
    @SubclassMapping(source = StrategyPositionAction.class, target = StrategyPositionAction.class)
    StrategyAction domainToMessageDefinition(StrategyAction action);

    @Mapping(target = "id", ignore = true)
    StrategyOrderAction domainToMessageDefinition(StrategyOrderAction action);

    @Mapping(target = "id", ignore = true)
    StrategyAlgoOrderAction domainToMessageDefinition(StrategyAlgoOrderAction action);

    @Mapping(target = "id", ignore = true)
    StrategyPositionAction domainToMessageDefinition(StrategyPositionAction action);

    /*
     * Коллекции узлов объявлены поимённо: коллекцию того же типа MapStruct
     * иначе копирует целиком, и ключи элементов уехали бы вместе с ней.
     */

    List<StrategyIndicatorSetting> indicatorSettingsToMessageDefinition(List<StrategyIndicatorSetting> settings);

    List<StrategyMarketStructureSetting> marketStructureSettingsToMessageDefinition(
            List<StrategyMarketStructureSetting> settings);

    List<StrategyDetail> detailsToMessageDefinition(List<StrategyDetail> details);

    List<StrategyTranche> tranchesToMessageDefinition(List<StrategyTranche> tranches);

    List<StrategyStep> stepsToMessageDefinition(List<StrategyStep> steps);

    List<StrategyAction> actionsToMessageDefinition(List<StrategyAction> actions);

    Map<Deal.Status, List<StrategyStep>> dealLevelStepsToMessageDefinition(
            Map<Deal.Status, List<StrategyStep>> steps);

    Map<DealTranche.Status, List<StrategyStep>> trancheStepsToMessageDefinition(
            Map<DealTranche.Status, List<StrategyStep>> steps);
}
