package com.example.tradingbot.domain.service.candlegroup.repair;

import java.util.List;

public record RepairResult(
    boolean countOkBeforeRepair,
    boolean countOkAfterRepair,
    List<TimeWindow> leafWindows,
    List<TimeWindow> gapWindows,
    List<GapRepairResult> repairedGaps
) {
}
