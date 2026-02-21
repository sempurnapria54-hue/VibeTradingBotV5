package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.ops.ReconcileOpsService;
import com.example.tradingbot.mapping.okxproxy.ReconcileReportMapper;
import com.example.tradingbot.rest.dto.ops.ReconcileRunResponse;
import com.example.tradingbot.rest.model.response.reconcile.ReconcileReportResponse;
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
    private final ReconcileReportMapper reconcileReportMapper;

    @PostMapping
    public ReconcileRunResponse run(@RequestParam String mode, @PathVariable(name = "exchangeId") Long exchangeId) {
        Long reportId = reconcileOpsService.run(mode, exchangeId);
        return new ReconcileRunResponse(reportId);
    }

    @GetMapping("/reports")
    public List<ReconcileReportResponse> getByExchange(@RequestParam(required = false) Integer limit,
                                                       @PathVariable(name = "exchangeId") Long exchangeId) {
        return reconcileReportMapper.domainToRest(reconcileOpsService.listReports(exchangeId, limit));
    }

    @GetMapping("/reports/instruments/{instrumentId}")
    public List<ReconcileReportResponse> getByInstrument(@PathVariable(name = "exchangeId") Long exchangeId,
                                                         @PathVariable(name = "instrumentId") String instrumentId,
                                                         @RequestParam(required = false) Integer limit) {
        return reconcileReportMapper.domainToRest(reconcileOpsService.listReportsByInstrument(exchangeId, instrumentId, limit));
    }

    @GetMapping("/reports/{id}")
    public ReconcileReportResponse getById(@PathVariable Long id) {
        return reconcileReportMapper.domainToRest(reconcileOpsService.getReport(id));
    }
}
