package com.example.async.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class HealthController {

    private final String podId;
    private final String clusterId;
    private final String serverPort;

    public HealthController(String podId,
            @Value("${app.cluster.id}") String clusterId,
            @Value("${server.port}") String serverPort) {
        this.podId = podId;
        this.clusterId = clusterId;
        this.serverPort = serverPort;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("podId", podId);
        response.put("clusterId", clusterId);
        response.put("port", serverPort);
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "基金系統運行正常");
        return ResponseEntity.ok(response);
    }
}