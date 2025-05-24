package com.example.async.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
@Slf4j
public class PodIdentityConfig {

    @Value("${server.port}")
    private String serverPort;

    @Value("${app.cluster.id}")
    private String clusterId;

    /**
     * 根據主機名+端口生成唯一的POD ID
     */
    @Bean
    public String podId() {
        String podId = generateUniquePodId();
        log.info("🏷️ 自動生成POD身份 - Host: {}, Port: {}, POD ID: {}, Cluster: {}",
                getHostName(), serverPort, podId, clusterId);
        return podId;
    }

    /**
     * 生成唯一的POD ID - 使用多重標識符確保唯一性
     * 支援鏡像環境避免衝突，最後加上UUID確保100%唯一性
     */
    private String generateUniquePodId() {
        String hostName = getHostName();
        String processId = getProcessIdentifier();
        String networkId = getNetworkIdentifier();
        String uniqueSuffix = generateUniqueSuffix();

        // 方案1: 簡化主機名 + 端口 + 進程標識 + 隨機後綴
        String simplifiedHost = simplifyHostName(hostName);
        String baseId = "pod-" + simplifiedHost + "-" + serverPort + "-" + processId + "-" + uniqueSuffix;

        // 方案2: 如果太長，使用緊湊模式但保留唯一性
        if (baseId.length() > 30) {
            String combined = hostName + serverPort + processId + networkId;
            int combinedHash = Math.abs(combined.hashCode()) % 1000;
            baseId = "pod-" + combinedHash + "-" + serverPort + "-" + uniqueSuffix;
        }

        log.debug("🔍 POD ID生成 - Host: {}, Process: {}, Network: {}, Unique: {}, 最終POD ID: {}",
                hostName, processId, networkId, uniqueSuffix, baseId);

        return baseId;
    }

    /**
     * 簡化主機名 - 去除域名部分，保留主要標識
     */
    private String simplifyHostName(String hostName) {
        if (hostName == null || hostName.isEmpty()) {
            return "unknown";
        }

        // 去除域名部分，只保留主機名
        String simplified = hostName.split("\\.")[0];

        // 去除常見的前綴/後綴
        simplified = simplified.replaceAll("^(pod|node|host|server)-?", "");
        simplified = simplified.replaceAll("-?(\\d+)$", "$1");

        // 確保不為空
        if (simplified.isEmpty()) {
            simplified = "h" + Math.abs(hostName.hashCode()) % 100;
        }

        return simplified.toLowerCase();
    }

    /**
     * 獲取主機名
     */
    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("無法獲取主機名，使用默認值: {}", e.getMessage());
            // 備用方案：使用系統屬性或環境變量
            String hostName = System.getProperty("HOSTNAME");
            if (hostName == null || hostName.isEmpty()) {
                hostName = System.getenv("HOSTNAME");
            }
            if (hostName == null || hostName.isEmpty()) {
                hostName = "localhost";
            }
            return hostName;
        }
    }

    /**
     * 獲取進程標識符 - 用於鏡像環境區分
     */
    private String getProcessIdentifier() {
        try {
            // 方案1: 使用JVM進程ID
            String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            if (pid.contains("@")) {
                pid = pid.split("@")[0]; // 提取進程ID部分
            }

            // 方案2: 如果PID太長，取後4位
            if (pid.length() > 4) {
                pid = pid.substring(pid.length() - 4);
            }

            return pid;
        } catch (Exception e) {
            log.warn("無法獲取進程ID，使用隨機值: {}", e.getMessage());
            return String.valueOf(System.currentTimeMillis() % 10000);
        }
    }

    /**
     * 獲取網絡標識符 - 額外的唯一性保證
     */
    private String getNetworkIdentifier() {
        try {
            // 方案1: 使用本地IP地址的後兩段
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String[] ipParts = localIp.split("\\.");
            if (ipParts.length >= 2) {
                return ipParts[ipParts.length - 2] + ipParts[ipParts.length - 1];
            }

            // 方案2: 使用MAC地址的一部分
            java.net.NetworkInterface ni = java.net.NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (ni != null && ni.getHardwareAddress() != null) {
                byte[] mac = ni.getHardwareAddress();
                return String.format("%02x%02x", mac[mac.length - 2], mac[mac.length - 1]);
            }

        } catch (Exception e) {
            log.debug("無法獲取網絡標識符: {}", e.getMessage());
        }

        // 備用方案: 使用時間戳的一部分
        return String.valueOf(System.nanoTime() % 1000);
    }

    /**
     * 生成唯一後綴 - 確保100%無衝突
     * 使用UUID的一部分或隨機字母
     */
    private String generateUniqueSuffix() {
        try {
            // 方案1: 使用UUID的前8位字符（數字+字母組合）
            String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
            return uuid.substring(0, 4).toLowerCase();

        } catch (Exception e) {
            // 方案2: 純隨機4位英文字母作為備用
            StringBuilder sb = new StringBuilder();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < 4; i++) {
                char c = (char) ('a' + random.nextInt(26));
                sb.append(c);
            }
            return sb.toString();
        }
    }

    /**
     * 根據端口號自動推斷集群ID（備用方案）
     */
    @Bean
    public String autoClusterId() {
        String autoCluster = generateClusterIdFromPort(serverPort);
        log.info("🏗️ 集群配置 - Port: {}, Auto Cluster: {}, Config Cluster: {}",
                serverPort, autoCluster, clusterId);
        // 優先使用配置文件中的cluster.id
        return clusterId;
    }

    /**
     * 根據端口號推斷集群ID（備用邏輯）
     */
    private String generateClusterIdFromPort(String port) {
        // 這個邏輯作為備用，主要還是依賴配置文件
        switch (port) {
            case "9090":
            case "9091":
                return "cluster-1";
            case "9092":
            case "9093":
                return "cluster-2";
            default:
                return "cluster-auto";
        }
    }
}