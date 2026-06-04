package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.LicenseEntity;
import com.atarashii.policyreport.persistence.LicenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 授权签发与校验。所有判定以数据库记录为准（在线授权）：
 * 签发 → 客户端首次激活绑定机器指纹 → 之后每次调用核心接口都校验。
 */
@Service
public class LicenseService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 去掉易混淆字符（0/O、1/I）的 32 字符表。 */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final LicenseRepository repository;

    public LicenseService(LicenseRepository repository) {
        this.repository = repository;
    }

    /** 一次校验/激活的结果。expiresAt 在失败时可能为 null。 */
    public record CheckResult(boolean ok, String reason, Instant expiresAt) {
    }

    /** 签发一张新授权（状态 ISSUED，尚未绑定机器）。 */
    @Transactional
    public LicenseEntity issue(String customerName, int validDays, String note) {
        LicenseEntity entity = new LicenseEntity();
        entity.setLicenseKey(generateUniqueKey());
        entity.setCustomerName(customerName == null || customerName.isBlank()
                ? "未命名客户" : customerName.trim());
        entity.setStatus(LicenseEntity.Status.ISSUED);
        Instant now = Instant.now();
        entity.setIssuedAt(now);
        entity.setExpiresAt(now.plus(Duration.ofDays(validDays <= 0 ? 365 : validDays)));
        entity.setNote(note);
        return repository.save(entity);
    }

    /** 客户端首次激活：绑定机器指纹。重复激活同机器视为成功。 */
    @Transactional
    public CheckResult activate(String licenseKey, String machineFingerprint) {
        LicenseEntity entity = repository.findByLicenseKey(normalizeKey(licenseKey)).orElse(null);
        if (entity == null) {
            return new CheckResult(false, "授权码不存在", null);
        }
        if (machineFingerprint == null || machineFingerprint.isBlank()) {
            return new CheckResult(false, "缺少设备标识，无法激活", null);
        }
        CheckResult basic = checkLifecycle(entity);
        if (basic != null) {
            return basic;
        }
        String machineHash = hash(machineFingerprint);
        if (entity.getMachineId() == null) {
            entity.setMachineId(machineHash);
            entity.setActivatedAt(Instant.now());
        } else if (!entity.getMachineId().equals(machineHash)) {
            return new CheckResult(false, "该授权码已在另一台设备激活，如需换机请联系供应方", null);
        }
        entity.setStatus(LicenseEntity.Status.ACTIVE);
        entity.setLastSeenAt(Instant.now());
        return new CheckResult(true, "激活成功", entity.getExpiresAt());
    }

    /** 受保护接口每次调用时校验：要求已激活、未过期、机器指纹匹配。 */
    @Transactional
    public CheckResult verify(String licenseKey, String machineFingerprint) {
        LicenseEntity entity = repository.findByLicenseKey(normalizeKey(licenseKey)).orElse(null);
        if (entity == null) {
            return new CheckResult(false, "授权无效，请先激活软件", null);
        }
        CheckResult basic = checkLifecycle(entity);
        if (basic != null) {
            return basic;
        }
        if (entity.getMachineId() == null) {
            return new CheckResult(false, "授权尚未激活，请先激活软件", null);
        }
        if (machineFingerprint == null
                || !entity.getMachineId().equals(hash(machineFingerprint))) {
            return new CheckResult(false, "设备标识不匹配，授权仅限激活设备使用", null);
        }
        entity.setLastSeenAt(Instant.now());
        return new CheckResult(true, "授权有效", entity.getExpiresAt());
    }

    public List<LicenseEntity> list() {
        return repository.findAll();
    }

    /** 吊销授权（不付费续费时使用）。 */
    @Transactional
    public boolean revoke(String licenseKey) {
        LicenseEntity entity = repository.findByLicenseKey(normalizeKey(licenseKey)).orElse(null);
        if (entity == null) {
            return false;
        }
        entity.setStatus(LicenseEntity.Status.REVOKED);
        return true;
    }

    /**
     * 检查吊销 / 过期等生命周期状态；通过返回 null，未通过返回失败结果。
     * 过期时顺手把状态落库为 EXPIRED。
     */
    private CheckResult checkLifecycle(LicenseEntity entity) {
        if (entity.getStatus() == LicenseEntity.Status.REVOKED) {
            return new CheckResult(false, "授权已被吊销，请联系供应方", null);
        }
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            if (entity.getStatus() != LicenseEntity.Status.EXPIRED) {
                entity.setStatus(LicenseEntity.Status.EXPIRED);
            }
            return new CheckResult(false, "授权已到期，请联系供应方续费", entity.getExpiresAt());
        }
        return null;
    }

    private String generateUniqueKey() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String key = generateKey();
            if (repository.findByLicenseKey(key).isEmpty()) {
                return key;
            }
        }
        throw new IllegalStateException("授权码生成失败：连续重复");
    }

    /** 形如 PRD-XXXX-XXXX-XXXX-XXXX。 */
    private String generateKey() {
        StringBuilder sb = new StringBuilder("PRD");
        for (int group = 0; group < 4; group++) {
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
        }
        return sb.toString();
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toUpperCase();
    }

    private static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
