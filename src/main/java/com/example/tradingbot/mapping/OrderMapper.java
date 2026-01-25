package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxOrderDto;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.rest.model.OrderRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {OrderAttachAlgoMapper.class, OrderLinkedAlgoMapper.class})
public interface OrderMapper {
    Order clientToDomain(OkxOrderDto dto);

    OrderRest domainToRest(Order domain);
}
