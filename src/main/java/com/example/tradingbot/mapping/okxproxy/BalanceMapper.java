package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.model.okx.BalanceResponse;
import com.example.tradingbot.domain.model.okxproxy.Balance;
import com.example.tradingbot.domain.model.okxproxy.BalanceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BalanceMapper {

    @Mapping(source = "ccy", target = "currency")
    @Mapping(source = "cashBal", target = "cashBalance")
    @Mapping(source = "availBal", target = "availableBalance")
    @Mapping(source = "eq", target = "equity")
    @Mapping(source = "frozenBal", target = "frozenBalance")
    @Mapping(source = "upl", target = "unrealizedProfit")
    Balance clientToDomain(BalanceResponse source);

    @Mapping(source = "currency", target = "ccy")
    @Mapping(source = "cashBalance", target = "cashBal")
    @Mapping(source = "availableBalance", target = "availBal")
    @Mapping(source = "equity", target = "eq")
    @Mapping(source = "frozenBalance", target = "frozenBal")
    @Mapping(source = "unrealizedProfit", target = "upl")
    BalanceResponse domainToClient(Balance source);





    com.example.tradingbot.client.model.okx.BalanceRequest domainToClient(BalanceRequest request);

    List<Balance> clientToDomain(List<com.example.tradingbot.client.model.okx.BalanceResponse> source);
}
