package com.example.async.service;

import com.example.async.model.TaskEvent;
import com.example.async.model.TaskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BondService {
    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatFutureMap = new ConcurrentHashMap<>();
    private static final String EVENT_TOPIC = "bond-events";
    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER = Executors.newScheduledThreadPool(2);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    // 用於提取基本correlationId的正則表達式
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^(.*?)-\\d+$");

    public SseEmitter createSseEmitter(String correlationId) {
        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        final String finalCorrelationId = correlationId;

        emitter.onCompletion(new Runnable() {
            @Override
            public void run() {
                log.info("債券系統 - 關聯 ID 為 {} 的 SSE 連線已完成", finalCorrelationId);
                sseEmitterMap.remove(finalCorrelationId);
                stopHeartbeat(finalCorrelationId);
            }
        });

        emitter.onTimeout(new Runnable() {
            @Override
            public void run() {
                log.info("債券系統 - 關聯 ID 為 {} 的 SSE 連線超時", finalCorrelationId);
                sseEmitterMap.remove(finalCorrelationId);
                stopHeartbeat(finalCorrelationId);
                emitter.complete();
            }
        });

        emitter.onError(throwable -> {
            log.error("債券系統 - 關聯 ID 為 {} 的 SSE 發生錯誤", finalCorrelationId, throwable);
            sseEmitterMap.remove(finalCorrelationId);
            stopHeartbeat(finalCorrelationId);
            emitter.complete();
        });

        try {
            // 發送初始連接建立事件
            TaskEvent connectEvent = TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("CONNECTED")
                    .message("債券系統 SSE連接已建立")
                    .finalEvent(false)
                    .build();

            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(connectEvent));

            sseEmitterMap.put(correlationId, emitter);
            startHeartbeat(correlationId);
            log.info("債券系統 - 已為關聯 ID {} 添加 SSE Emitter 到映射中", correlationId);
        } catch (IOException e) {
            log.error("債券系統 - 向關聯 ID 為 {} 的 SSE 發送初始事件時出錯", correlationId, e);
            stopHeartbeat(correlationId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void startHeartbeat(String correlationId) {
        log.info("債券系統 - 啟動心跳機制，關聯 ID: {}, 心跳間隔: {}秒", correlationId, HEARTBEAT_INTERVAL_SECONDS);

        ScheduledFuture<?> future = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(() -> {
            SseEmitter emitter = sseEmitterMap.get(correlationId);
            if (emitter != null) {
                try {
                    log.info("債券系統 - 發送心跳到 SSE 連線，關聯 ID: {}", correlationId);
                    TaskEvent heartbeatEvent = TaskEvent.builder()
                            .correlationId(correlationId)
                            .status("HEARTBEAT")
                            .message("債券系統心跳檢測")
                            .finalEvent(false)
                            .build();

                    // 直接發送心跳事件
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(System.currentTimeMillis()))
                            .name("HEARTBEAT")
                            .data(heartbeatEvent)
                            .reconnectTime(0));

                    log.info("債券系統 - 心跳事件已發送，關聯 ID: {}", correlationId);
                } catch (IOException e) {
                    log.error("債券系統 - 發送心跳到關聯 ID 為 {} 的 SSE 時出錯: {}", correlationId, e.getMessage());
                    stopHeartbeat(correlationId);
                    sseEmitterMap.remove(correlationId);
                    emitter.completeWithError(e);
                }
            } else {
                // 如果emitter不存在，則停止心跳
                log.info("債券系統 - 找不到關聯 ID 為 {} 的 SSE emitter，停止心跳", correlationId);
                stopHeartbeat(correlationId);
            }
        }, 2, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS); // 2秒後開始，然後每10秒一次

        heartbeatFutureMap.put(correlationId, future);
    }

    private void stopHeartbeat(String correlationId) {
        ScheduledFuture<?> future = heartbeatFutureMap.remove(correlationId);
        if (future != null) {
            future.cancel(false);
            log.info("債券系統 - 已停止關聯 ID 為 {} 的心跳", correlationId);
        }
    }

    @Async
    public void processTaskAsync(TaskRequest request) {
        String correlationId = request.getCorrelationId();
        log.info("債券系統 - 開始處理關聯 ID 為 {} 的異步任務", correlationId);

        try {
            // 發布處理中事件
            publishEvent(TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("PROCESSING")
                    .message("債券任務已開始處理")
                    .finalEvent(false)
                    .build());

            // 執行子任務
            for (int i = 0; i < request.getNumberOfSubtasks(); i++) {
                executeSubtask(correlationId, i);
            }

            // 所有任務完成時發布最終事件
            publishEvent(TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("COMPLETED")
                    .message("所有債券任務已完成")
                    .finalEvent(true)
                    .build());

        } catch (Exception e) {
            log.error("債券系統 - 處理關聯 ID 為 {} 的任務時出錯", correlationId, e);
            publishEvent(TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("FAILED")
                    .message("債券任務處理失敗: " + e.getMessage())
                    .finalEvent(true)
                    .build());
        }
    }

    private void executeSubtask(String correlationId, int subtaskId) {
        try {
            // 模擬耗時操作
            Thread.sleep(2500); // 債券處理時間略長於基金

            // 發布子任務完成事件
            publishEvent(TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("SUBTASK_COMPLETED")
                    .message("債券子任務 " + subtaskId + " 已完成")
                    .result("子任務 " + subtaskId + " 的結果")
                    .finalEvent(false)
                    .build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("債券系統 - 關聯 ID 為 {} 的子任務被中斷", correlationId, e);
        }
    }

    private void publishEvent(TaskEvent event) {
        try {
            log.info("債券系統 - 向 Kafka 發布事件: {}", event);
            kafkaTemplate.send(EVENT_TOPIC, event.getCorrelationId(), event);
        } catch (Exception e) {
            log.error("債券系統 - 發布事件到 Kafka 失敗: {}", e.getMessage(), e);
        }
    }

    /**
     * 從correlationId提取基本ID
     * 例如從 "abc-bond-0" 提取 "abc-bond"
     */
    private String extractBaseCorrelationId(String correlationId) {
        if (correlationId == null)
            return null;

        Matcher matcher = CORRELATION_ID_PATTERN.matcher(correlationId);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        return correlationId;
    }

    public void handleEvent(TaskEvent event) {
        String correlationId = event.getCorrelationId();
        log.info("債券系統 - 處理關聯 ID 為 {} 的事件", correlationId);

        // 嘗試提取基本correlationId
        String baseCorrelationId = extractBaseCorrelationId(correlationId);
        log.info("債券系統 - 從 {} 提取出基本correlationId: {}", correlationId, baseCorrelationId);

        // 首先嘗試使用基本correlationId查找emitter
        SseEmitter emitter = sseEmitterMap.get(baseCorrelationId);

        // 如果找不到，再嘗試使用完整correlationId
        if (emitter == null) {
            log.info("債券系統 - 找不到基本correlationId {}, 嘗試使用完整correlationId", baseCorrelationId);
            emitter = sseEmitterMap.get(correlationId);
        }

        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getStatus())
                        .data(event));

                if (event.isFinalEvent()) {
                    // 完成連接前先停止心跳
                    if (sseEmitterMap.containsKey(baseCorrelationId)) {
                        stopHeartbeat(baseCorrelationId);
                        sseEmitterMap.remove(baseCorrelationId);
                        log.info("債券系統 - 關聯 ID 為 {} 的 SSE 已完成", baseCorrelationId);
                    } else if (sseEmitterMap.containsKey(correlationId)) {
                        stopHeartbeat(correlationId);
                        sseEmitterMap.remove(correlationId);
                        log.info("債券系統 - 關聯 ID 為 {} 的 SSE 已完成", correlationId);
                    }
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("債券系統 - 向 SSE 發送事件時出錯", e);
                if (sseEmitterMap.containsKey(baseCorrelationId)) {
                    stopHeartbeat(baseCorrelationId);
                    sseEmitterMap.remove(baseCorrelationId);
                } else if (sseEmitterMap.containsKey(correlationId)) {
                    stopHeartbeat(correlationId);
                    sseEmitterMap.remove(correlationId);
                }
                emitter.completeWithError(e);
            }
        } else {
            log.warn("債券系統 - 找不到關聯 ID 為 {} 或基本ID {} 的 SSE emitter", correlationId, baseCorrelationId);
        }
    }
}