package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.BalanceRequest;
import com.example.tradingbot.client.model.okx.response.BalanceResponse;
import com.example.tradingbot.domain.model.Balance;
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
    Balance clientOkxResponseToDomain(BalanceResponse source);

    @Mapping(source = "currency", target = "ccy")
    @Mapping(source = "cashBalance", target = "cashBal")
    @Mapping(source = "availableBalance", target = "availBal")
    @Mapping(source = "equity", target = "eq")
    @Mapping(source = "frozenBalance", target = "frozenBal")
    @Mapping(source = "unrealizedProfit", target = "upl")
    BalanceResponse domainToClient(Balance source);

    List<Balance> clientOkxResponseToDomain(List<BalanceResponse> source);

    BalanceRequest domainToClientOkxRequest(Balance source);
}
