package com.atarashii.policyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String policyCardPath = "../../2025重大项目用地政策明白卡.pdf";
    private String templatePolicyPath = "../../自然资源部办公厅关于进一步规范建设用地报批文本格式的函（自然资办函〔2024〕1009号）.pdf";
    private String ollamaBaseUrl = "http://localhost:11434";
    private String ollamaModel = "qwen25vl-7b-local:latest";
    private String ollamaFallbackModel = "qwen25vl-3b-local:latest";
    private boolean ollamaEnabled = true;
    private int ollamaTimeoutSeconds = 45;

    public String getPolicyCardPath() {
        return policyCardPath;
    }

    public void setPolicyCardPath(String policyCardPath) {
        this.policyCardPath = policyCardPath;
    }

    public String getTemplatePolicyPath() {
        return templatePolicyPath;
    }

    public void setTemplatePolicyPath(String templatePolicyPath) {
        this.templatePolicyPath = templatePolicyPath;
    }

    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public String getOllamaModel() {
        return ollamaModel;
    }

    public void setOllamaModel(String ollamaModel) {
        this.ollamaModel = ollamaModel;
    }

    public String getOllamaFallbackModel() {
        return ollamaFallbackModel;
    }

    public void setOllamaFallbackModel(String ollamaFallbackModel) {
        this.ollamaFallbackModel = ollamaFallbackModel;
    }

    public boolean isOllamaEnabled() {
        return ollamaEnabled;
    }

    public void setOllamaEnabled(boolean ollamaEnabled) {
        this.ollamaEnabled = ollamaEnabled;
    }

    public int getOllamaTimeoutSeconds() {
        return ollamaTimeoutSeconds;
    }

    public void setOllamaTimeoutSeconds(int ollamaTimeoutSeconds) {
        this.ollamaTimeoutSeconds = ollamaTimeoutSeconds;
    }
}