package com.example.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class SimpleLoadBalancer {
    private static final Logger logger = Logger.getLogger(SimpleLoadBalancer.class.getName());
    private static final List<String> BACKEND_SERVERS = Arrays.asList(
            "http://localhost:9090",
            "http://localhost:9091");
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ProxyHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        logger.info("反向代理服務器啟動在端口 " + port);
        logger.info("後端服務器: " + BACKEND_SERVERS);
    }

    static class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 添加 CORS 標頭
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*"); // 允許所有請求頭
            exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600"); // 預檢請求快取時間

            // 處理 OPTIONS 預檢請求
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); // 204 No Content 常用於預檢請求成功
                exchange.close();
                return;
            }

            // 獲取目標服務器（簡單的輪詢負載均衡）
            String targetServer = getNextServer();

            try {
                // 構建目標 URL
                String path = exchange.getRequestURI().toString();
                URL url = new URL(targetServer + path);

                // 特殊處理 SSE 請求，確保 SSE 連接保持穩定
                boolean isSseRequest = path.contains("/events/");

                if (isSseRequest) {
                    logger.info("檢測到 SSE 請求: " + path + " -> " + targetServer);
                }

                // 創建連接
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(exchange.getRequestMethod());

                // 複製請求頭
                for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
                    String key = header.getKey();
                    for (String value : header.getValue()) {
                        connection.addRequestProperty(key, value);
                    }
                }

                // 如果有請求體，複製請求體
                if ("POST".equals(exchange.getRequestMethod()) || "PUT".equals(exchange.getRequestMethod())) {
                    connection.setDoOutput(true);
                    try (InputStream is = exchange.getRequestBody();
                            OutputStream os = connection.getOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) != -1) {
                            os.write(buffer, 0, length);
                        }
                    }
                }

                // 獲取響應狀態
                int responseCode = connection.getResponseCode();

                // 複製響應頭
                // 注意：這裡先清除可能由 exchange 預設的 Content-Type，以確保後端的優先
                exchange.getResponseHeaders().remove("Content-Type");
                for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                    String key = header.getKey();
                    if (key != null) { // 忽略狀態行
                        // 特別處理 Content-Type，使用 set 而不是 add，避免重複
                        if (key.equalsIgnoreCase("Content-Type")) {
                            // 使用後端返回的第一個 Content-Type 值 (如果有的話)
                            if (!header.getValue().isEmpty()) {
                                exchange.getResponseHeaders().set(key, header.getValue().get(0));
                            }
                        } else {
                            // 其他標頭，可以 add 多個值
                            for (String value : header.getValue()) {
                                exchange.getResponseHeaders().add(key, value);
                            }
                        }
                    }
                }

                // ***** 強制修正 SSE 的 Content-Type *****
                if (isSseRequest) {
                    logger.info("偵測到 SSE 請求，強制設定 Content-Type 為 text/event-stream");
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
                    // SSE 通常需要禁用快取並保持連線
                    exchange.getResponseHeaders().set("Cache-Control",
                            "no-cache, no-store, max-age=0, must-revalidate");
                    exchange.getResponseHeaders().set("Pragma", "no-cache");
                    exchange.getResponseHeaders().set("Expires", "0");
                    exchange.getResponseHeaders().set("Connection", "keep-alive");
                }

                // 在發送響應頭之前確保 CORS 標頭已添加 (之前已添加)
                exchange.sendResponseHeaders(responseCode, 0);

                // 複製響應體
                try (InputStream is = connection.getInputStream();
                        OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) != -1) {
                        os.write(buffer, 0, length);
                    }
                } catch (IOException e) {
                    // 處理錯誤響應
                    try (InputStream es = connection.getErrorStream();
                            OutputStream os = exchange.getResponseBody()) {
                        if (es != null) {
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = es.read(buffer)) != -1) {
                                os.write(buffer, 0, length);
                            }
                        } else {
                            byte[] errorMessage = e.getMessage().getBytes();
                            os.write(errorMessage);
                        }
                    }
                }

                logger.info(String.format("[%s] %s %s -> %s %d",
                        isSseRequest ? "SSE" : "HTTP",
                        exchange.getRequestMethod(),
                        exchange.getRequestURI(),
                        targetServer,
                        responseCode));

            } catch (Exception e) {
                logger.severe("代理請求錯誤: " + e.getMessage());
                String response = "代理錯誤: " + e.getMessage();
                // 確保錯誤響應也有 CORS 標頭 (上面已添加)
                exchange.sendResponseHeaders(500, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } finally {
                exchange.close();
            }
        }

        private String getNextServer() {
            // 簡單的輪詢負載均衡
            int index = counter.getAndIncrement() % BACKEND_SERVERS.size();
            return BACKEND_SERVERS.get(index);
        }
    }
}