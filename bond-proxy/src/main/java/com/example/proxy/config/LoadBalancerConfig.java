package com.example.proxy.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LoadBalancerConfig extends AbstractGatewayFilterFactory<LoadBalancerConfig.Config> {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Map<String, String> stickySessionMap = new ConcurrentHashMap<>();

    public LoadBalancerConfig() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(final Config config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                ServerHttpRequest request = exchange.getRequest();
                String path = request.getURI().getPath();
                String method = request.getMethod().name();

                log.debug("Received request: {} {}", method, path);

                // 如果是SSE請求（events路徑），使用粘性會話
                if (path.contains("/events/")) {
                    return handleStickySession(exchange, chain, config, path);
                } else {
                    // 對於普通請求，使用輪詢
                    return handleRoundRobin(exchange, chain, config);
                }
            }
        };
    }

    private Mono<Void> handleStickySession(ServerWebExchange exchange,
            GatewayFilterChain chain,
            Config config,
            String path) {
        // 從路徑中提取correlationId
        String[] pathParts = path.split("/");
        String correlationId = pathParts[pathParts.length - 1];
        log.debug("SSE request with correlationId: {}", correlationId);

        // 檢查是否已經有粘性會話
        String existingTarget = stickySessionMap.get(correlationId);
        if (existingTarget != null) {
            log.debug("Using existing sticky session for correlationId {}: {}", correlationId, existingTarget);
            URI uri = URI.create(existingTarget);
            ServerHttpRequest newRequest = exchange.getRequest().mutate().uri(uri).build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        }

        // 如果沒有現有會話，選擇一個負載最小的目標
        Target target = config.getTargets().get(counter.getAndIncrement() % config.getTargets().size());
        String targetUri = target.getUri();

        // 保存會話
        stickySessionMap.put(correlationId, targetUri);
        log.debug("Created new sticky session for correlationId {}: {}", correlationId, targetUri);

        URI uri = URI.create(targetUri + path);
        ServerHttpRequest newRequest = exchange.getRequest().mutate().uri(uri).build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    private Mono<Void> handleRoundRobin(ServerWebExchange exchange,
            GatewayFilterChain chain,
            Config config) {
        String path = exchange.getRequest().getURI().getPath();

        // 簡單的輪詢負載均衡
        Target target = config.getTargets().get(counter.getAndIncrement() % config.getTargets().size());
        String targetUri = target.getUri();
        log.debug("Round-robin selected target: {}", targetUri);

        URI uri = URI.create(targetUri + path);
        ServerHttpRequest newRequest = exchange.getRequest().mutate().uri(uri).build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.singletonList("roundRobin");
    }

    @Data
    public static class Config {
        private boolean roundRobin = true;
        private List<Target> targets;
    }

    @Data
    public static class Target {
        private String uri;
        private int weight;
    }
}