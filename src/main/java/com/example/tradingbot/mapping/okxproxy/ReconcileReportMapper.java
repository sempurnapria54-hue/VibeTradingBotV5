package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.domain.model.entity.SynchronizeExecutionEnvironmentReportAnomalyEntity;
import com.example.tradingbot.domain.model.entity.SynchronizeExecutionEnvironmentReportEntity;
import com.example.tradingbot.rest.model.response.reconcile.ReconcileReportAnomalyResponse;
import com.example.tradingbot.rest.model.response.reconcile.ReconcileReportResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReconcileReportMapper {

    ReconcileReportResponse domainToRest(SynchronizeExecutionEnvironmentReportEntity source);

    List<ReconcileReportResponse> domainToRest(List<SynchronizeExecutionEnvironmentReportEntity> source);

    ReconcileReportAnomalyResponse domainToRest(SynchronizeExecutionEnvironmentReportAnomalyEntity source);

    List<ReconcileReportAnomalyResponse> anomaliesToRest(List<SynchronizeExecutionEnvironmentReportAnomalyEntity> source);
}
