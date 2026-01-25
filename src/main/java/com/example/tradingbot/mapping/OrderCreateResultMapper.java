package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxCreateOrderResult;
import com.example.tradingbot.domain.model.OrderCreateResult;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderCreateResultMapper {

    OrderCreateResult clientToDomain(OkxCreateOrderResult result);

    List<OrderCreateResult> clientToDomain(List<OkxCreateOrderResult> results);

    com.example.tradingbot.rest.model.OrderCreateResult domainToRest(OrderCreateResult result);

    List<com.example.tradingbot.rest.model.OrderCreateResult> domainToRest(List<OrderCreateResult> results);
}
