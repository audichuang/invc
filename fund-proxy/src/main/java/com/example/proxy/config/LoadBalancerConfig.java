package com.example.proxy.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class LoadBalancerConfig extends AbstractGatewayFilterFactory<LoadBalancerConfig.Config> {

    private final AtomicInteger counter = new AtomicInteger(0);

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

                // 隨機選擇目標或使用輪詢
                Target target;
                if (config.isRandomSelection()) {
                    // 隨機選擇
                    int randomIndex = ThreadLocalRandom.current().nextInt(config.getTargets().size());
                    target = config.getTargets().get(randomIndex);
                    log.debug("Random selected target: {}", target.getUri());
                } else {
                    // 輪詢
                    target = config.getTargets().get(counter.getAndIncrement() % config.getTargets().size());
                    log.debug("Round-robin selected target: {}", target.getUri());
                }

                String targetUri = target.getUri();
                URI uri = URI.create(targetUri + path);
                ServerHttpRequest newRequest = exchange.getRequest().mutate().uri(uri).build();
                return chain.filter(exchange.mutate().request(newRequest).build());
            }
        };
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.singletonList("randomSelection");
    }

    @Data
    public static class Config {
        private boolean randomSelection = false; // 預設使用輪詢
        private List<Target> targets;
    }

    @Data
    public static class Target {
        private String uri;
        private int weight; // 保留權重欄位供未來擴展
    }
}