package com.example.tradingbot.mapping;

import com.example.tradingbot.rest.model.response.ReconcileReportAnomalyResponse;
import com.example.tradingbot.rest.model.response.ReconcileReportResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReconcileReportMapper {

    @Mapping(source = "exchangeId", target = "exchangeInternalId")
    ReconcileReportResponse domainToRest(ReconcileReportEntity source);

    List<ReconcileReportResponse> domainToRest(List<ReconcileReportEntity> source);

    ReconcileReportAnomalyResponse domainToRest(ReconcileAnomalyEntity source);

    List<ReconcileReportAnomalyResponse> anomaliesToRest(List<ReconcileAnomalyEntity> source);
}
