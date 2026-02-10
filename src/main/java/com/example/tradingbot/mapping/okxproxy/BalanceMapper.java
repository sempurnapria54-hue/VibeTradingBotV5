package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.BalanceDto;
import com.example.tradingbot.domain.model.okxproxy.Balance;
import com.example.tradingbot.rest.model.okxproxy.BalanceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BalanceMapper {

    @Mapping(source = "ccy", target = "currency")
    @Mapping(source = "cashBal", target = "cashBalance")
    @Mapping(source = "availBal", target = "availableBalance")
    @Mapping(source = "eq", target = "equity")
    @Mapping(source = "frozenBal", target = "frozenBalance")
    @Mapping(source = "upl", target = "unrealizedProfit")
    Balance clientToDomain(BalanceDto source);

    @Mapping(source = "currency", target = "ccy")
    @Mapping(source = "cashBalance", target = "cashBal")
    @Mapping(source = "availableBalance", target = "availBal")
    @Mapping(source = "equity", target = "eq")
    @Mapping(source = "frozenBalance", target = "frozenBal")
    @Mapping(source = "unrealizedProfit", target = "upl")
    BalanceDto domainToClient(Balance source);

    com.example.tradingbot.rest.model.okxproxy.Balance domainToRest(Balance source);

    Balance restToDomain(com.example.tradingbot.rest.model.okxproxy.Balance source);

    com.example.tradingbot.domain.model.okxproxy.BalanceRequest restToDomain(BalanceRequest source);
}
