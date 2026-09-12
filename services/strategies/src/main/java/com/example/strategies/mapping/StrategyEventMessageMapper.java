package com.example.strategies.mapping;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.message.StrategyActivatedMessage;
import com.example.tradingbot.message.StrategyLifecycleMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

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
 * поле {@code definition} переносится как есть, а не перекладывается
 * по полям.
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
    @Mapping(target = "definition", expression = "java(definition)")
    StrategyActivatedMessage domainToActivatedMessage(Strategy definition, String actor);

    /** Прочие переходы: дерево не едет — копия у потребителя уже есть. */
    @Mapping(target = "strategyInternalId", source = "definition.internalId")
    @Mapping(target = "exchangeAccountInternalId", source = "definition.exchangeAccountInternalId")
    @Mapping(target = "instrumentInternalId", source = "definition.instrumentInternalId")
    StrategyLifecycleMessage domainToLifecycleMessage(Strategy definition, String actor);
}
