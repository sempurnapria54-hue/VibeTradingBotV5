package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxOrderAttachedAlgo;
import com.example.tradingbot.domain.model.OrderAttachedAlgo;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderAttachedAlgoMapper {

    OrderAttachedAlgo clientToDomain(OkxOrderAttachedAlgo order);

    List<OrderAttachedAlgo> clientToDomain(List<OkxOrderAttachedAlgo> orders);

    com.example.tradingbot.rest.model.OrderAttachedAlgo domainToRest(OrderAttachedAlgo order);

    List<com.example.tradingbot.rest.model.OrderAttachedAlgo> domainToRest(List<OrderAttachedAlgo> orders);
}
