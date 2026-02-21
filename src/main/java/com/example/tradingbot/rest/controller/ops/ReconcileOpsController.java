package com.example.tradingbot.rest.controller.ops;

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
@RequestMapping("/api/ops/reconcile")
@RequiredArgsConstructor
public class ReconcileOpsController {

    private final ReconcileOpsService reconcileOpsService;

    @PostMapping("/run")
    public ReconcileRunResponse run(
        @RequestParam String mode,
        @RequestParam Long exchangeId
    ) {
        Long reportId = reconcileOpsService.run(mode, exchangeId);
        return new ReconcileRunResponse(reportId);
    }

    @GetMapping("/reports")
    public List<SynchronizeExecutionEnvironmentReportEntity> listReports(
        @RequestParam Long exchangeId,
        @RequestParam(required = false) Integer limit
    ) {
        return reconcileOpsService.listReports(exchangeId, limit);
    }

    @GetMapping("/reports/{id}")
    public SynchronizeExecutionEnvironmentReportEntity getReport(@PathVariable Long id) {
        return reconcileOpsService.getReport(id);
    }
}
