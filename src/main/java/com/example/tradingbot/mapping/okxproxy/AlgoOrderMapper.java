package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.AlgoOrderDto;
import com.example.tradingbot.domain.model.okxproxy.AlgoOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AlgoOrderMapper {

    @Mapping(source = "algoId", target = "algoOrderId")
    @Mapping(source = "clOrdId", target = "clientOrderId")
    @Mapping(source = "instId", target = "instrumentId")
    @Mapping(source = "ordType", target = "orderType")
    @Mapping(source = "sCode", target = "statusCode")
    @Mapping(source = "sMsg", target = "statusMessage")
    AlgoOrder clientToDomain(AlgoOrderDto source);

    @Mapping(source = "algoOrderId", target = "algoId")
    @Mapping(source = "clientOrderId", target = "clOrdId")
    @Mapping(source = "instrumentId", target = "instId")
    @Mapping(source = "orderType", target = "ordType")
    @Mapping(source = "statusCode", target = "sCode")
    @Mapping(source = "statusMessage", target = "sMsg")
    AlgoOrderDto domainToClient(AlgoOrder source);

    com.example.tradingbot.rest.model.okxproxy.AlgoOrder domainToRest(AlgoOrder source);

    AlgoOrder restToDomain(com.example.tradingbot.rest.model.okxproxy.AlgoOrder source);
}
