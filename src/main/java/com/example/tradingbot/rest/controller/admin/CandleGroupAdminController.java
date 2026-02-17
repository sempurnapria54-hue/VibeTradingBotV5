package com.example.tradingbot.rest.controller.admin;

import com.example.tradingbot.domain.model.admin.CandleGroupBootstrapRequest;
import com.example.tradingbot.domain.model.admin.CandleGroupView;
import com.example.tradingbot.domain.service.admin.CandleGroupOpsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CandleGroupAdminController {

    private final CandleGroupOpsService candleGroupOpsService;

    @PostMapping("/instruments/{instrumentId}/candle-groups/bootstrap")
    public List<CandleGroupView> bootstrap(@PathVariable Long instrumentId,
                                           @RequestBody CandleGroupBootstrapRequest request) {
        return candleGroupOpsService.bootstrap(instrumentId, request);
    }

    @GetMapping("/instruments/{instrumentId}/candle-groups")
    public List<CandleGroupView> listByInstrument(@PathVariable Long instrumentId) {
        return candleGroupOpsService.listByInstrument(instrumentId);
    }

    @PostMapping("/candle-groups/{groupId}/run-once")
    public void runOnce(@PathVariable Long groupId) {
        candleGroupOpsService.runOnce(groupId);
    }
}
