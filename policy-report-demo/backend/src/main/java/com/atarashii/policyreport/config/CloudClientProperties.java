package com.atarashii.policyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端构建（client profile）连接云端服务器所需的配置，前缀 app.cloud-client。
 * 仅在客户端那侧使用：核心判定接口改为 HTTP 调用服务器。
 */
@ConfigurationProperties(prefix = "app.cloud-client")
public class CloudClientProperties {

    /** 云端服务器基址，如 https://report.example.com（结尾不带斜杠）。 */
    private String baseUrl = "";

    /** 本机的授权码，客户首次激活时填入。 */
    private String licenseKey = "";

    /**
     * 机器指纹覆盖值。留空（默认）则由 {@code MachineFingerprint.current()} 自动计算，
     * 一般不需要手填。
     */
    private String machineId = "";

    /** 调用云端接口的超时（秒）。 */
    private int timeoutSeconds = 30;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        // 容错：去掉结尾斜杠，避免拼出 //api
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey == null ? "" : licenseKey.trim();
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId == null ? "" : machineId.trim();
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
