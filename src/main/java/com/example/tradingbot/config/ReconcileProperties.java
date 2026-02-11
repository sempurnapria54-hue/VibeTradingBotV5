package com.example.tradingbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reconcile")
public class ReconcileProperties {

    private boolean enabled = false;
    private final CancelFlow cancelFlow = new CancelFlow();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CancelFlow getCancelFlow() {
        return cancelFlow;
    }

    public static class CancelFlow {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
