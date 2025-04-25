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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {
    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    private static final String EVENT_TOPIC = "task-events";

    // 用於提取基本correlationId的正則表達式
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^(.*?)-\\d+$");

    public SseEmitter createSseEmitter(String correlationId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            log.info("關聯 ID 為 {} 的 SSE 連線已完成", correlationId);
            sseEmitterMap.remove(correlationId);
        });

        emitter.onTimeout(() -> {
            log.info("關聯 ID 為 {} 的 SSE 連線超時", correlationId);
            sseEmitterMap.remove(correlationId);
            emitter.complete();
        });

        emitter.onError(ex -> {
            log.error("關聯 ID 為 {} 的 SSE 發生錯誤", correlationId, ex);
            sseEmitterMap.remove(correlationId);
            emitter.complete();
        });

        try {
            // 發送初始連接建立事件
            TaskEvent connectEvent = TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("CONNECTED")
                    .message("SSE連接已建立")
                    .finalEvent(false)
                    .build();

            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(connectEvent));

            sseEmitterMap.put(correlationId, emitter);
            log.info("已為關聯 ID {} 添加 SSE Emitter 到映射中", correlationId);
        } catch (IOException e) {
            log.error("向關聯 ID 為 {} 的 SSE 發送初始事件時出錯", correlationId, e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Async
    public void processTaskAsync(TaskRequest request) {
        String correlationId = request.getCorrelationId();
        log.info("開始處理關聯 ID 為 {} 的異步任務", correlationId);

        try {
            // 發布處理中事件
            publishEvent(TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("PROCESSING")
                    .message("任務已開始處理")
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
                    .message("所有任務已完成")
                    .finalEvent(true)
                    .build());

        } catch (Exception e) {
            log.error("處理關聯 ID 為 {} 的任務時出錯", correlationId, e);
            publishEvent(TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("FAILED")
                    .message("任務處理失敗: " + e.getMessage())
                    .finalEvent(true)
                    .build());
        }
    }

    private void executeSubtask(String correlationId, int subtaskId) {
        try {
            // 模擬耗時操作
            Thread.sleep(2000);

            // 發布子任務完成事件
            publishEvent(TaskEvent.builder()
                    .correlationId(correlationId)
                    .status("SUBTASK_COMPLETED")
                    .message("子任務 " + subtaskId + " 已完成")
                    .result("子任務 " + subtaskId + " 的結果")
                    .finalEvent(false)
                    .build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("關聯 ID 為 {} 的子任務被中斷", correlationId, e);
        }
    }

    private void publishEvent(TaskEvent event) {
        log.info("向 Kafka 發布事件: {}", event);
        kafkaTemplate.send(EVENT_TOPIC, event.getCorrelationId(), event);
    }

    /**
     * 從correlationId提取基本ID
     * 例如從 "abc-fund-0" 提取 "abc-fund"
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
        log.info("處理關聯 ID 為 {} 的事件", correlationId);

        // 嘗試提取基本correlationId
        String baseCorrelationId = extractBaseCorrelationId(correlationId);
        log.info("從 {} 提取出基本correlationId: {}", correlationId, baseCorrelationId);

        // 首先嘗試使用基本correlationId查找emitter
        SseEmitter emitter = sseEmitterMap.get(baseCorrelationId);

        // 如果找不到，再嘗試使用完整correlationId
        if (emitter == null) {
            log.info("找不到基本correlationId {}, 嘗試使用完整correlationId", baseCorrelationId);
            emitter = sseEmitterMap.get(correlationId);
        }

        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getStatus())
                        .data(event));

                if (event.isFinalEvent()) {
                    emitter.complete();
                    // 根據實際使用的ID移除
                    if (sseEmitterMap.containsKey(baseCorrelationId)) {
                        sseEmitterMap.remove(baseCorrelationId);
                        log.info("關聯 ID 為 {} 的 SSE 已完成", baseCorrelationId);
                    } else if (sseEmitterMap.containsKey(correlationId)) {
                        sseEmitterMap.remove(correlationId);
                        log.info("關聯 ID 為 {} 的 SSE 已完成", correlationId);
                    }
                }
            } catch (IOException e) {
                log.error("向 SSE 發送事件時出錯", e);
                emitter.completeWithError(e);
                if (sseEmitterMap.containsKey(baseCorrelationId)) {
                    sseEmitterMap.remove(baseCorrelationId);
                } else if (sseEmitterMap.containsKey(correlationId)) {
                    sseEmitterMap.remove(correlationId);
                }
            }
        } else {
            log.warn("找不到關聯 ID 為 {} 或基本ID {} 的 SSE emitter", correlationId, baseCorrelationId);
        }
    }
}