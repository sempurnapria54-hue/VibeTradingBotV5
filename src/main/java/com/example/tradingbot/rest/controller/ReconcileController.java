package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.ops.ReconcileServiceFacade;
import com.example.tradingbot.mapping.okxproxy.ReconcileReportMapper;
import com.example.tradingbot.rest.model.response.ReconcileReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/{exchangeId}/reconcile")
@RequiredArgsConstructor
public class ReconcileController {

    private final ReconcileServiceFacade reconcileService;
    private final ReconcileReportMapper reconcileReportMapper;

    @PostMapping
    public ReconcileReportResponse run(@PathVariable(name = "exchangeId") String exchangeId) {
        var reportEntity = reconcileService.run(exchangeId);
        return reconcileReportMapper.domainToRest(reportEntity);
    }

    @GetMapping
    public List<ReconcileReportResponse> getByExchange(@RequestParam(required = false) Integer limit,
                                                       @PathVariable(name = "exchangeId") String exchangeId) {
        return reconcileReportMapper.domainToRest(reconcileService.getByExchange(exchangeId, limit));
    }

    @GetMapping("/instruments/{instrumentId}")
    public List<ReconcileReportResponse> getByInstrument(@PathVariable(name = "exchangeId") String exchangeId,
                                                         @PathVariable(name = "instrumentId") String instrumentId,
                                                         @RequestParam(required = false) Integer limit) {
        return reconcileReportMapper.domainToRest(reconcileService.getByInstrument(exchangeId, instrumentId, limit));
    }

    @GetMapping("/{id}")
    public ReconcileReportResponse getById(@PathVariable(name = "id") String internalId) {
        return reconcileReportMapper.domainToRest(reconcileService.getByInternalId(internalId));
    }
}
