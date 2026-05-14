package com.atarashii.policyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret = ""; // 由 application-local.yml 或 JWT_SECRET 环境变量注入，禁止硬编码
    private long jwtExpirationMs = 2592000000L;
    private String policyCardPath = "../../2025重大项目用地政策明白卡.pdf";
    private String templatePolicyPath = "../../自然资源部办公厅关于进一步规范建设用地报批文本格式的函（自然资办函〔2024〕1009号）.pdf";
    private String ollamaBaseUrl = "http://localhost:11434";
    private String ollamaModel = "qwen25vl-7b-local:latest";
    private String ollamaFallbackModel = "qwen25vl-3b-local:latest";
    private boolean ollamaEnabled = true;
    private int ollamaTimeoutSeconds = 45;
    private int aiFullTextChunkSize = 12_000;
    private int aiFullTextMaxChunks = 80;
    private boolean ocrEnabled = true;
    private String ocrEngine = "auto";
    private int ocrMaxPages = 120;
    private int ocrDpi = 160;
    private int ocrMinUsefulChars = 1_500;
    private String ocrTesseractCommand = "tesseract";
    private String ocrTesseractLanguage = "chi_sim+eng";
    private String ocrTesseractDataPath = "";
    private int ocrTesseractPageSegMode = 6;
    private int ocrTesseractTimeoutSeconds = 90;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public long getJwtExpirationMs() { return jwtExpirationMs; }
    public void setJwtExpirationMs(long jwtExpirationMs) { this.jwtExpirationMs = jwtExpirationMs; }

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

    public int getAiFullTextChunkSize() {
        return aiFullTextChunkSize;
    }

    public void setAiFullTextChunkSize(int aiFullTextChunkSize) {
        this.aiFullTextChunkSize = aiFullTextChunkSize;
    }

    public int getAiFullTextMaxChunks() {
        return aiFullTextMaxChunks;
    }

    public void setAiFullTextMaxChunks(int aiFullTextMaxChunks) {
        this.aiFullTextMaxChunks = aiFullTextMaxChunks;
    }

    public boolean isOcrEnabled() {
        return ocrEnabled;
    }

    public void setOcrEnabled(boolean ocrEnabled) {
        this.ocrEnabled = ocrEnabled;
    }

    public String getOcrEngine() {
        return ocrEngine;
    }

    public void setOcrEngine(String ocrEngine) {
        this.ocrEngine = ocrEngine;
    }

    public int getOcrMaxPages() {
        return ocrMaxPages;
    }

    public void setOcrMaxPages(int ocrMaxPages) {
        this.ocrMaxPages = ocrMaxPages;
    }

    public int getOcrDpi() {
        return ocrDpi;
    }

    public void setOcrDpi(int ocrDpi) {
        this.ocrDpi = ocrDpi;
    }

    public int getOcrMinUsefulChars() {
        return ocrMinUsefulChars;
    }

    public void setOcrMinUsefulChars(int ocrMinUsefulChars) {
        this.ocrMinUsefulChars = ocrMinUsefulChars;
    }

    public String getOcrTesseractCommand() {
        return ocrTesseractCommand;
    }

    public void setOcrTesseractCommand(String ocrTesseractCommand) {
        this.ocrTesseractCommand = ocrTesseractCommand;
    }

    public String getOcrTesseractLanguage() {
        return ocrTesseractLanguage;
    }

    public void setOcrTesseractLanguage(String ocrTesseractLanguage) {
        this.ocrTesseractLanguage = ocrTesseractLanguage;
    }

    public String getOcrTesseractDataPath() {
        return ocrTesseractDataPath;
    }

    public void setOcrTesseractDataPath(String ocrTesseractDataPath) {
        this.ocrTesseractDataPath = ocrTesseractDataPath;
    }

    public int getOcrTesseractPageSegMode() {
        return ocrTesseractPageSegMode;
    }

    public void setOcrTesseractPageSegMode(int ocrTesseractPageSegMode) {
        this.ocrTesseractPageSegMode = ocrTesseractPageSegMode;
    }

    public int getOcrTesseractTimeoutSeconds() {
        return ocrTesseractTimeoutSeconds;
    }

    public void setOcrTesseractTimeoutSeconds(int ocrTesseractTimeoutSeconds) {
        this.ocrTesseractTimeoutSeconds = ocrTesseractTimeoutSeconds;
    }
}