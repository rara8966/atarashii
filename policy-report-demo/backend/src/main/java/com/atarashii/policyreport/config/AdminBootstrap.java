package com.atarashii.policyreport.config;

import com.atarashii.policyreport.service.AuthService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动后若系统尚无 ADMIN，自动种子 admin/admin123。
 * 让客户拿到新装的客户端就能直接登录，不用手动 register。
 * <p>
 * 已经存在 ADMIN 的情况下不会动；老库升级、服务器多用户场景都不受影响。
 */
@Component
public class AdminBootstrap {

    private final AuthService authService;

    public AdminBootstrap(AuthService authService) {
        this.authService = authService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedAdminIfEmpty() {
        if (authService.hasAdmin()) {
            return;
        }
        try {
            authService.register("admin", "admin123", "ADMIN", null);
        } catch (Exception ignored) {
            // 并发竞争或其他路径已建：忽略
        }
    }
}
