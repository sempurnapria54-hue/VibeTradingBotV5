package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.model.entity.SynchronizeExecutionEnvironmentReportEntity;
import com.example.tradingbot.domain.service.ops.ReconcileOpsService;
import com.example.tradingbot.rest.dto.ops.ReconcileRunResponse;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{exchangeId}/reconcile")
@RequiredArgsConstructor
public class ReconcileController {

    private final ReconcileOpsService reconcileOpsService;

    @PostMapping
    public ReconcileRunResponse run(@RequestParam String mode, @PathVariable(name = "exchangeId") Long exchangeId) {
        Long reportId = reconcileOpsService.run(mode, exchangeId);
        return new ReconcileRunResponse(reportId);
    }

    @GetMapping("/reports")
    public List<SynchronizeExecutionEnvironmentReportEntity> getByExchange(@RequestParam(required = false) Integer limit,
                                                                           @PathVariable(name = "exchangeId") Long exchangeId) {
        return reconcileOpsService.listReports(exchangeId, limit);
    }

    @GetMapping("/reports/{id}")
    public SynchronizeExecutionEnvironmentReportEntity getById(@PathVariable Long id) {
        return reconcileOpsService.getReport(id);
    }
}
