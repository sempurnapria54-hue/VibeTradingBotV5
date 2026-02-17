package com.example.tradingbot.rest.controller.ops;

import com.example.tradingbot.domain.service.ops.InstrumentDataReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops/instruments")
@RequiredArgsConstructor
public class InstrumentOpsController {

    private final InstrumentDataReadinessService instrumentDataReadinessService;

    @PostMapping("/{id}/recompute-status")
    public void recomputeStatus(@PathVariable Long id) {
        instrumentDataReadinessService.recomputeInstrumentStatusFromCandleGroups(id);
    }
}
