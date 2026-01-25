package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxCancelOrderResult;
import com.example.tradingbot.domain.model.OrderCancelResult;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderCancelResultMapper {

    OrderCancelResult clientToDomain(OkxCancelOrderResult result);

    List<OrderCancelResult> clientToDomain(List<OkxCancelOrderResult> results);

    com.example.tradingbot.rest.model.OrderCancelResult domainToRest(OrderCancelResult result);

    List<com.example.tradingbot.rest.model.OrderCancelResult> domainToRest(List<OrderCancelResult> results);
}
