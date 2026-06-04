package com.atarashii.policyreport.controller;

import com.atarashii.policyreport.config.LicenseProperties;
import com.atarashii.policyreport.persistence.LicenseEntity;
import com.atarashii.policyreport.service.LicenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 授权接口。
 * /api/license/* 给客户端调用（公开）；
 * /api/admin/licenses/* 给供应方管理（需 X-Admin-Token）。
 */
@RestController
@RequestMapping("/api")
public class LicenseController {

    private final LicenseService licenseService;
    private final LicenseProperties properties;

    public LicenseController(LicenseService licenseService, LicenseProperties properties) {
        this.licenseService = licenseService;
        this.properties = properties;
    }

    /** 客户端首次激活。body: { licenseKey, machineId } */
    @PostMapping("/license/activate")
    public ResponseEntity<Map<String, Object>> activate(@RequestBody Map<String, String> body) {
        LicenseService.CheckResult result =
                licenseService.activate(body.get("licenseKey"), body.get("machineId"));
        return toResponse(result);
    }

    /** 客户端定期复检授权是否仍有效。body: { licenseKey, machineId } */
    @PostMapping("/license/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        LicenseService.CheckResult result =
                licenseService.verify(body.get("licenseKey"), body.get("machineId"));
        return toResponse(result);
    }

    /** 签发新授权。body: { customerName, validDays, note } */
    @PostMapping("/admin/licenses")
    public ResponseEntity<Map<String, Object>> issue(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        if (!adminAuthorized(token)) {
            return forbidden();
        }
        String customerName = asString(body.get("customerName"));
        int validDays = asInt(body.get("validDays"), 365);
        String note = asString(body.get("note"));
        LicenseEntity entity = licenseService.issue(customerName, validDays, note);
        return ResponseEntity.ok(toView(entity));
    }

    /** 列出全部授权。 */
    @GetMapping("/admin/licenses")
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!adminAuthorized(token)) {
            return forbidden();
        }
        List<Map<String, Object>> views = licenseService.list().stream()
                .map(LicenseController::toView)
                .toList();
        return ResponseEntity.ok(views);
    }

    /** 吊销授权（不续费时使用）。 */
    @PostMapping("/admin/licenses/{key}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable("key") String key) {
        if (!adminAuthorized(token)) {
            return forbidden();
        }
        boolean done = licenseService.revoke(key);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", done);
        resp.put("message", done ? "已吊销" : "授权码不存在");
        return ResponseEntity.status(done ? 200 : 404).body(resp);
    }

    private boolean adminAuthorized(String token) {
        String expected = properties.getAdminToken();
        return expected != null && !expected.isBlank()
                && expected.equals(token);
    }

    private static ResponseEntity<Map<String, Object>> forbidden() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", false);
        resp.put("error", "管理员令牌无效");
        return ResponseEntity.status(403).body(resp);
    }

    private static ResponseEntity<Map<String, Object>> toResponse(LicenseService.CheckResult result) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", result.ok());
        resp.put("message", result.reason());
        if (result.expiresAt() != null) {
            resp.put("expiresAt", result.expiresAt().toString());
        }
        return ResponseEntity.status(result.ok() ? 200 : 403).body(resp);
    }

    private static Map<String, Object> toView(LicenseEntity entity) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("licenseKey", entity.getLicenseKey());
        view.put("customerName", entity.getCustomerName());
        view.put("status", entity.getStatus().name());
        view.put("bound", entity.getMachineId() != null);
        view.put("issuedAt", str(entity.getIssuedAt()));
        view.put("activatedAt", str(entity.getActivatedAt()));
        view.put("expiresAt", str(entity.getExpiresAt()));
        view.put("lastSeenAt", str(entity.getLastSeenAt()));
        view.put("note", entity.getNote());
        return view;
    }

    private static String str(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // 落到 fallback
            }
        }
        return fallback;
    }
}
