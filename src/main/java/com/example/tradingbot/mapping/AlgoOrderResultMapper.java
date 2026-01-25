package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxCancelAlgoOrderResult;
import com.example.tradingbot.client.okx.dto.OkxCreateAlgoOrderResult;
import com.example.tradingbot.domain.model.AlgoOrderResult;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlgoOrderResultMapper {

    AlgoOrderResult clientToDomain(OkxCreateAlgoOrderResult result);

    AlgoOrderResult clientToDomain(OkxCancelAlgoOrderResult result);

    List<AlgoOrderResult> clientToDomain(List<OkxCreateAlgoOrderResult> results);

    List<AlgoOrderResult> clientToDomainCancel(List<OkxCancelAlgoOrderResult> results);

    com.example.tradingbot.rest.model.AlgoOrderResult domainToRest(AlgoOrderResult result);

    List<com.example.tradingbot.rest.model.AlgoOrderResult> domainToRest(List<AlgoOrderResult> results);
}
