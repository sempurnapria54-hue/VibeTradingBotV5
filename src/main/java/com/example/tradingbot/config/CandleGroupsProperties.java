package com.example.tradingbot.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "candle-groups.job")
public class CandleGroupsProperties {

    public enum IntegrityCheckMode {
        NONE,
        COUNT_ONLY,
        COUNT_PLUS_REPAIR
    }

    private boolean enabled = false;
    private int maxGroupsPerRun = 10;
    private int leaseDurationSec = 60;
    private int maxAttemptsBeforeError = 5;
    private int batchLimit = 300;
    private long fixedDelayMs = 10_000L;
    private Map<String, Integer> tailOverlapBars = new HashMap<>();
    private IntegrityCheckMode integrityCheckMode = IntegrityCheckMode.NONE;
    private int syncIntegrityEveryNRuns = 0;
    private int repairLeafBars = 64;
    private int leaseExtendEveryBatches = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxGroupsPerRun() {
        return maxGroupsPerRun;
    }

    public void setMaxGroupsPerRun(int maxGroupsPerRun) {
        this.maxGroupsPerRun = maxGroupsPerRun;
    }

    public int getLeaseDurationSec() {
        return leaseDurationSec;
    }

    public void setLeaseDurationSec(int leaseDurationSec) {
        this.leaseDurationSec = leaseDurationSec;
    }

    public int getMaxAttemptsBeforeError() {
        return maxAttemptsBeforeError;
    }

    public void setMaxAttemptsBeforeError(int maxAttemptsBeforeError) {
        this.maxAttemptsBeforeError = maxAttemptsBeforeError;
    }

    public int getBatchLimit() {
        return batchLimit;
    }

    public void setBatchLimit(int batchLimit) {
        this.batchLimit = batchLimit;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public Map<String, Integer> getTailOverlapBars() {
        return tailOverlapBars;
    }

    public void setTailOverlapBars(Map<String, Integer> tailOverlapBars) {
        this.tailOverlapBars = tailOverlapBars;
    }

    public IntegrityCheckMode getIntegrityCheckMode() {
        return integrityCheckMode;
    }

    public void setIntegrityCheckMode(IntegrityCheckMode integrityCheckMode) {
        this.integrityCheckMode = integrityCheckMode;
    }

    public int getSyncIntegrityEveryNRuns() {
        return syncIntegrityEveryNRuns;
    }

    public void setSyncIntegrityEveryNRuns(int syncIntegrityEveryNRuns) {
        this.syncIntegrityEveryNRuns = syncIntegrityEveryNRuns;
    }

    public int getRepairLeafBars() {
        return repairLeafBars;
    }

    public void setRepairLeafBars(int repairLeafBars) {
        this.repairLeafBars = repairLeafBars;
    }

    public int getLeaseExtendEveryBatches() {
        return leaseExtendEveryBatches;
    }

    public void setLeaseExtendEveryBatches(int leaseExtendEveryBatches) {
        this.leaseExtendEveryBatches = leaseExtendEveryBatches;
    }
}
