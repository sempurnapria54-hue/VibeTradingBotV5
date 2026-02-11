package com.example.tradingbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "synchronize-execution-environment")
public class SynchronizeExecutionEnvironmentProperties {

    private final Reports reports = new Reports();

    public Reports getReports() {
        return reports;
    }

    public static class Reports {

        private int retentionDays = 14;

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }
}

