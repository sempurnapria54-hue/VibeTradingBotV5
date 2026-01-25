package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxBalanceDetailDto;
import com.example.tradingbot.domain.model.BalanceDetail;
import com.example.tradingbot.rest.model.BalanceDetailRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BalanceDetailMapper {
    BalanceDetail clientToDomain(OkxBalanceDetailDto dto);

    BalanceDetailRest domainToRest(BalanceDetail domain);
}
