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
     * 生成唯一的POD ID - 使用主機名+端口確保唯一性
     */
    private String generateUniquePodId() {
        String hostName = getHostName();

        // 方案1: 簡化主機名 + 端口
        String simplifiedHost = simplifyHostName(hostName);
        String podId = "pod-" + simplifiedHost + "-" + serverPort;

        // 方案2: 如果主機名太長，使用hashCode
        if (podId.length() > 20) {
            int hostHash = Math.abs(hostName.hashCode()) % 1000;
            podId = "pod-" + hostHash + "-" + serverPort;
        }

        log.debug("🔍 POD ID生成 - 原始主機名: {}, 簡化後: {}, 最終POD ID: {}",
                hostName, simplifiedHost, podId);

        return podId;
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