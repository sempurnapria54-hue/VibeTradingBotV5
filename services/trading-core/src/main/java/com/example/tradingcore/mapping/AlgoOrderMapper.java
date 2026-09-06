package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.persistence.model.AlgoOrderEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг отдельной условной заявки domain ↔ persistence
 * (docs/models/mapping/AlgoOrder.md).
 *
 * <p>Дерево условия и перечень идентификаторов порождённых заявок едут
 * через {@link RuntimeJsonConverter}: в колонке они лежат навесом, и их
 * разбор — не перенос полей.
 *
 * <p>Конвертер приезжает конструктором, а не полем: маппер с навесом
 * собирается и вне контейнера — в тесте, который проверяет обе половины
 * навеса.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = RuntimeJsonConverter.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AlgoOrderMapper {

    AlgoOrderEntity domainToPersistence(AlgoOrder algoOrder);

    AlgoOrder persistenceToDomain(AlgoOrderEntity entity);

    /**
     * Перенос добытых фактов площадки на нашу условную заявку.
     *
     * <p>Не переносятся: наш клиентский идентификатор и связи строки, наше
     * дерево условия (площадка отдаёт эхо своих полей, а не нашу
     * декларацию), статус и причина закрытия — их назначает исполнитель
     * доменным переходом (docs/rules/external-status-resolution.md).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "condition", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    void updateFromFetched(AlgoOrder fetched, @MappingTarget AlgoOrder algoOrder);
}
