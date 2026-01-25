package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxBalance;
import com.example.tradingbot.domain.model.Balance;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = BalanceCurrencyDetailMapper.class)
public interface BalanceMapper {

    Balance clientToDomain(OkxBalance balance);

    List<Balance> clientToDomain(List<OkxBalance> balances);

    com.example.tradingbot.rest.model.Balance domainToRest(Balance balance);

    List<com.example.tradingbot.rest.model.Balance> domainToRest(List<Balance> balances);
}
