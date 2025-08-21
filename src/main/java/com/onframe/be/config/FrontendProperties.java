package com.onframe.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "onframe")
public class FrontendProperties {
    private Cors cors = new Cors();
    private Air air = new Air();

    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Air getAir() { return air; }
    public void setAir(Air air) { this.air = air; }

    public static class Cors {
        private List<String> allowedOrigins = List.of("*");
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Air {
        private int historySize = 200;
        public int getHistorySize() { return historySize; }
        public void setHistorySize(int historySize) { this.historySize = historySize; }
    }
}