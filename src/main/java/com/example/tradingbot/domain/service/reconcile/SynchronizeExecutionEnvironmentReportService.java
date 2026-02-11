package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.DatabaseSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportEntity;
import com.example.tradingbot.persistence.service.SynchronizeExecutionEnvironmentReportDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SynchronizeExecutionEnvironmentReportService {

    private final SynchronizeExecutionEnvironmentReportDataService reportDataService;
    private final ObjectMapper objectMapper;

    public SynchronizeExecutionEnvironmentReportEntity createStartedReport(
        ExchangeEntity exchange,
        String trigger,
        DatabaseSnapshot databaseBefore,
        ExchangeSnapshot exchangeBefore
    ) {
        return reportDataService.createStarted(
            exchange,
            trigger,
            toJson(databaseBefore),
            toJson(exchangeBefore),
            Instant.now()
        );
    }

    private String toJson(Object source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize reconcile snapshot", exception);
        }
    }
}
