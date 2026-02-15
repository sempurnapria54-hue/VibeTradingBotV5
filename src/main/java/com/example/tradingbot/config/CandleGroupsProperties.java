package com.example.tradingbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "candle-groups.job")
public class CandleGroupsProperties {

    private boolean enabled = false;
    private int maxGroupsPerRun = 10;
    private int leaseDurationSec = 60;
    private int maxAttemptsBeforeError = 5;
    private long fixedDelayMs = 10_000L;

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

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }
}
