package com.atarashii.policyreport.security;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 计算一台机器的稳定指纹。
 * <p>
 * 取所有非回环网卡的 MAC 地址（排序后拼接）+ 操作系统名 + 系统架构，
 * 同一台机器多次运行结果一致；指纹只在本地拼装，发送给服务器时由服务器再做 SHA-256。
 * <p>
 * 注意：这是“够用”的指纹，不是防篡改方案——换网卡/虚拟机克隆会变。
 * 真正的约束力来自服务器端授权记录与到期校验。
 */
public final class MachineFingerprint {

    private MachineFingerprint() {
    }

    /** 返回本机指纹原文（未哈希）。拿不到任何 MAC 时退化为操作系统信息。 */
    public static String current() {
        List<String> macs = new ArrayList<>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    continue;
                }
                StringBuilder hex = new StringBuilder(mac.length * 2);
                for (byte b : mac) {
                    hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                    hex.append(Character.forDigit(b & 0xF, 16));
                }
                macs.add(hex.toString());
            }
        } catch (Exception ignored) {
            // 网卡枚举失败：退化为下方的操作系统信息
        }
        Collections.sort(macs);

        String os = System.getProperty("os.name", "?");
        String arch = System.getProperty("os.arch", "?");
        return "MFP|" + String.join(",", macs) + "|" + os + "|" + arch;
    }
}
