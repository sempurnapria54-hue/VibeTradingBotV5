package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxBalanceDetail;
import com.example.tradingbot.domain.model.BalanceCurrencyDetail;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface BalanceCurrencyDetailMapper {

    BalanceCurrencyDetail clientToDomain(OkxBalanceDetail detail);

    List<BalanceCurrencyDetail> clientToDomain(List<OkxBalanceDetail> details);

    com.example.tradingbot.rest.model.BalanceCurrencyDetail domainToRest(BalanceCurrencyDetail detail);

    List<com.example.tradingbot.rest.model.BalanceCurrencyDetail> domainToRest(List<BalanceCurrencyDetail> details);
}
