package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.domain.model.entity.ReconcileAnomalyEntity;
import com.example.tradingbot.domain.model.entity.ReconcileReportEntity;
import com.example.tradingbot.rest.model.response.ReconcileReportAnomalyResponse;
import com.example.tradingbot.rest.model.response.ReconcileReportResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReconcileReportMapper {

    @Mapping(source = "exchange.internalId", target = "exchangeInternalId")
    ReconcileReportResponse domainToRest(ReconcileReportEntity source);

    List<ReconcileReportResponse> domainToRest(List<ReconcileReportEntity> source);

    ReconcileReportAnomalyResponse domainToRest(ReconcileAnomalyEntity source);

    List<ReconcileReportAnomalyResponse> anomaliesToRest(List<ReconcileAnomalyEntity> source);
}
