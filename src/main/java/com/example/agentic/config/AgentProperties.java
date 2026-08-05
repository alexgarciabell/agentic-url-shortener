package com.example.agentic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private final Model model = new Model();
    private final Deployment deployment = new Deployment();
    private int maxRepairAttempts = 3;

    public Model getModel() {
        return model;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public int getMaxRepairAttempts() {
        return maxRepairAttempts;
    }

    public void setMaxRepairAttempts(int value) {
        this.maxRepairAttempts = value;
    }

    public static class Model {
        private String mode = "demo", baseUrl = "https://api.openai.com/v1", apiKey = "", model = "gpt-5";

        public String getMode() {
            return mode;
        }

        public void setMode(String v) {
            mode = v;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String v) {
            baseUrl = v;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String v) {
            apiKey = v;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String v) {
            model = v;
        }
    }

    public static class Deployment {
        private int portStart = 8090, portEnd = 8190;
        private Duration startupTimeout = Duration.ofSeconds(30);

        public int getPortStart() {
            return portStart;
        }

        public void setPortStart(int v) {
            portStart = v;
        }

        public int getPortEnd() {
            return portEnd;
        }

        public void setPortEnd(int v) {
            portEnd = v;
        }

        public Duration getStartupTimeout() {
            return startupTimeout;
        }

        public void setStartupTimeout(Duration v) {
            startupTimeout = v;
        }
    }
}
