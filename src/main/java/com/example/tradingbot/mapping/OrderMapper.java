package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxOrder;
import com.example.tradingbot.domain.model.Order;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = OrderAttachedAlgoMapper.class)
public interface OrderMapper {

    @Mapping(target = "linkedAlgoId", source = "linkedAlgoOrder.algoId")
    Order clientToDomain(OkxOrder order);

    List<Order> clientToDomain(List<OkxOrder> orders);

    com.example.tradingbot.rest.model.Order domainToRest(Order order);

    List<com.example.tradingbot.rest.model.Order> domainToRest(List<Order> orders);
}
