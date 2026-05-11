package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.UserEntity;
import com.atarashii.policyreport.persistence.UserRepository;
import com.atarashii.policyreport.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuthService {
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "REPORTER", "VIEWER");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public boolean hasAdmin() {
        return userRepository.existsByRole("ADMIN");
    }

    @Transactional
    public AuthResult register(String username, String password, String role, String requesterRole) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("用户名不能为空");
        if (password == null || password.length() < 4) throw new IllegalArgumentException("密码长度不能少于4位");
        if (userRepository.existsByUsername(username)) throw new IllegalArgumentException("用户名已存在: " + username);

        String normalizedRole = role == null || role.isBlank() ? "REPORTER" : role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("非法角色: " + normalizedRole);
        }

        boolean adminExists = hasAdmin();
        if (adminExists) {
            // 已经有 ADMIN：仅 ADMIN 可创建账号
            if (!"ADMIN".equals(requesterRole)) {
                throw new IllegalArgumentException("仅管理员可创建账号");
            }
        } else {
            // 系统首次注册：强制为 ADMIN，确保 bootstrap 有管理员
            normalizedRole = "ADMIN";
        }

        UserEntity user = new UserEntity();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(normalizedRole);
        userRepository.save(user);

        String token = jwtUtil.generate(user.getUsername(), user.getRole());
        return new AuthResult(token, user.getUsername(), user.getRole());
    }

    public AuthResult login(String username, String password) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
        if (!user.isEnabled()) throw new IllegalArgumentException("账号已被禁用");
        if (!passwordEncoder.matches(password, user.getPasswordHash())) throw new IllegalArgumentException("账号或密码错误");
        String token = jwtUtil.generate(user.getUsername(), user.getRole());
        return new AuthResult(token, user.getUsername(), user.getRole());
    }

    public record AuthResult(String token, String username, String role) {}
}
