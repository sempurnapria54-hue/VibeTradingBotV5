package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxAmendOrderResult;
import com.example.tradingbot.domain.model.OrderAmendResult;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderAmendResultMapper {

    OrderAmendResult clientToDomain(OkxAmendOrderResult result);

    List<OrderAmendResult> clientToDomain(List<OkxAmendOrderResult> results);

    com.example.tradingbot.rest.model.OrderAmendResult domainToRest(OrderAmendResult result);

    List<com.example.tradingbot.rest.model.OrderAmendResult> domainToRest(List<OrderAmendResult> results);
}
