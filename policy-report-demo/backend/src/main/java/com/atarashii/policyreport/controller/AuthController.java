package com.atarashii.policyreport.controller;

import com.atarashii.policyreport.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        try {
            AuthService.AuthResult result = authService.login(body.get("username"), body.get("password"));
            return ResponseEntity.ok(Map.of(
                "token", result.token(),
                "username", result.username(),
                "role", result.role()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> body, Authentication auth) {
        try {
            String role = body.getOrDefault("role", "REPORTER");
            String requesterRole = null;
            if (auth != null && auth.isAuthenticated()) {
                requesterRole = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring(5))
                    .findFirst()
                    .orElse(null);
            }
            AuthService.AuthResult result = authService.register(body.get("username"), body.get("password"), role, requesterRole);
            return ResponseEntity.ok(Map.of(
                "token", result.token(),
                "username", result.username(),
                "role", result.role()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 提供给前端登录页：判断是否系统首注册（决定是否显示角色下拉） */
    @GetMapping("/bootstrap")
    public Map<String, Boolean> bootstrap() {
        return Map.of("hasAdmin", authService.hasAdmin());
    }
}
