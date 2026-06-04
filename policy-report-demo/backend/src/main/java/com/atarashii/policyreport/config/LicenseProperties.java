package com.atarashii.policyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 授权（License）相关配置，前缀 app.license。
 * 由 {@code @ConfigurationPropertiesScan} 自动注册。
 */
@ConfigurationProperties(prefix = "app.license")
public class LicenseProperties {

    /**
     * 授权校验总开关。
     * false=不校验（本地开发 / 老演示模式，行为与改造前一致）；
     * true=服务器生产模式，命中受保护路径的请求必须携带有效授权。
     */
    private boolean enabled = false;

    /**
     * 受保护的接口路径（Ant 风格）。命中这些路径的请求必须携带有效授权头。
     * 默认覆盖功能区合规判定相关的“皇冠宝石”接口。
     */
    private List<String> protectedPatterns = List.of(
            "/api/v2/projects/*/functional-zone-verdict",
            "/api/v2/projects/*/verdicts/refresh",
            "/api/v2/projects/*/steps/*/verdict",
            "/api/v2/projects/*/export/*"
    );

    /**
     * 管理员令牌：签发 / 查询 / 吊销授权的管理接口凭证。
     * 留空（默认）则所有 /api/admin/licenses 接口一律拒绝，避免裸奔。
     */
    private String adminToken = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getProtectedPatterns() {
        return protectedPatterns;
    }

    public void setProtectedPatterns(List<String> protectedPatterns) {
        this.protectedPatterns = protectedPatterns;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }
}
