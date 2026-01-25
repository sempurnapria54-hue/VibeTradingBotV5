package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxBalanceAccountDto;
import com.example.tradingbot.domain.model.BalanceAccount;
import com.example.tradingbot.rest.model.BalanceAccountRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = BalanceDetailMapper.class)
public interface BalanceAccountMapper {
    BalanceAccount clientToDomain(OkxBalanceAccountDto dto);

    BalanceAccountRest domainToRest(BalanceAccount domain);
}
