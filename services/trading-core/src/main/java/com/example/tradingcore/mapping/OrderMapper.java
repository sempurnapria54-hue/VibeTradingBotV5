package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.persistence.model.AttachedAlgoOrderEntity;
import com.example.tradingcore.persistence.model.OrderEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг ноги и её встроенной защиты domain ↔ persistence
 * (docs/models/mapping/Order.md).
 *
 * <p>Форм источника здесь нет: разговор с площадкой ведёт коннектор, а
 * ядро видит только доменную модель (docs/architecture/contracts.md).
 *
 * <p>Встроенная защита — дочерние строки, а не поле ноги: оркестрацию
 * коллекции держит {@code OrderDataService}, каскада у строки нет.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    OrderEntity domainToPersistence(Order order);

    Order persistenceToDomain(OrderEntity entity);

    AttachedAlgoOrderEntity domainToPersistence(AttachedAlgoOrder attached);

    AttachedAlgoOrder persistenceToDomain(AttachedAlgoOrderEntity entity);

    /**
     * Перенос добытых фактов площадки на нашу ногу.
     *
     * <p>Не переносятся: наш клиентский идентификатор и связи строки
     * (сделка, транш, эпизод) — они наши и площадке неизвестны; бизнес-тип
     * ноги — он наше решение о её роли; встроенные защиты — у них своя
     * строка и свой резолв состояния; статус и причина закрытия — их
     * назначает исполнитель доменным переходом, а причина write-once
     * (docs/rules/external-status-resolution.md).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    @Mapping(target = "attachedAlgoOrders", ignore = true)
    void updateFromFetched(Order fetched, @MappingTarget Order order);
}
