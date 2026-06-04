package com.atarashii.policyreport.security;

import com.atarashii.policyreport.config.LicenseProperties;
import com.atarashii.policyreport.service.LicenseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 授权网关：命中 {@link LicenseProperties#getProtectedPatterns()} 的请求，
 * 必须携带 X-License-Key + X-Machine-Id 且校验通过，否则直接 403。
 *
 * <p>故意不加 {@code @Component}，由 SecurityConfig 显式 new 出来挂进过滤链，
 * 避免 Spring Boot 把它当普通 Servlet Filter 再自动注册一次（重复执行）。
 */
public class LicenseFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final LicenseProperties properties;
    private final LicenseService licenseService;

    public LicenseFilter(LicenseProperties properties, LicenseService licenseService) {
        this.properties = properties;
        this.licenseService = licenseService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = pathWithinApplication(request);
        for (String pattern : properties.getProtectedPatterns()) {
            if (PATH_MATCHER.match(pattern, path)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String licenseKey = request.getHeader("X-License-Key");
        String machineId = request.getHeader("X-Machine-Id");
        LicenseService.CheckResult result = licenseService.verify(licenseKey, machineId);
        if (!result.ok()) {
            writeForbidden(response, result.reason());
            return;
        }
        chain.doFilter(request, response);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path;
    }

    private static void writeForbidden(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"" + jsonEscape(reason) + "\",\"status\":403,\"licenseError\":true}");
    }

    private static String jsonEscape(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
