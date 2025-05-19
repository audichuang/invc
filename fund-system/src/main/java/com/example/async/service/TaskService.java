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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {
    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatFutureMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sseConnectionTaskIdsMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sseConnectionCompletedTasksMap = new ConcurrentHashMap<>();
    private static final String EVENT_TOPIC = "task-events";
    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER = Executors.newScheduledThreadPool(2);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    // 用於提取基本correlationId的正則表達式
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^(.*?)-\\d+$");
    // 用於從單個任務ID中提取SSE連線ID (例如從 abc-fund-0 提取 abc-fund)
    private static final Pattern SSE_CONNECTION_ID_EXTRACTOR_PATTERN = Pattern.compile("^(.*)-[^-]+$");

    public SseEmitter createSseEmitter(String sseConnectionId, List<String> taskIds) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 如果提供了 taskIds，則初始化相關追蹤
        if (taskIds != null && !taskIds.isEmpty()) {
            log.info("SSE 連線 {} 將追蹤任務 IDs: {}", sseConnectionId, taskIds);
            sseConnectionTaskIdsMap.put(sseConnectionId, new ArrayList<>(taskIds));
            sseConnectionCompletedTasksMap.put(sseConnectionId, new HashSet<>());
        } else {
            log.info("SSE 連線 {} 不追蹤特定任務 IDs (或 taskIds 為空)", sseConnectionId);
        }

        emitter.onCompletion(() -> {
            log.info("關聯 ID 為 {} 的 SSE 連線已完成", sseConnectionId);
            cleanupSseResources(sseConnectionId);
        });

        emitter.onTimeout(() -> {
            log.info("關聯 ID 為 {} 的 SSE 連線超時", sseConnectionId);
            cleanupSseResources(sseConnectionId);
            emitter.complete();
        });

        emitter.onError(ex -> {
            log.error("關聯 ID 為 {} 的 SSE 發生錯誤", sseConnectionId, ex);
            cleanupSseResources(sseConnectionId);
            emitter.complete(); // 或者 emitter.completeWithError(ex) 如果不想讓客戶端重試
        });

        try {
            // 發送初始連接建立事件
            TaskEvent connectEvent = TaskEvent.builder()
                    .correlationId(sseConnectionId) // 使用 SSE 連線 ID
                    .status("CONNECTED")
                    .message("SSE連接已建立")
                    .finalEvent(false)
                    .build();

            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(connectEvent));

            sseEmitterMap.put(sseConnectionId, emitter);
            startHeartbeat(sseConnectionId);
            log.info("已為關聯 ID {} 添加 SSE Emitter 到映射中", sseConnectionId);
        } catch (IOException e) {
            log.error("向關聯 ID 為 {} 的 SSE 發送初始事件時出錯", sseConnectionId, e);
            stopHeartbeat(sseConnectionId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void startHeartbeat(String correlationId) {
        log.info("啟動心跳機制，關聯 ID: {}, 心跳間隔: {}秒", correlationId, HEARTBEAT_INTERVAL_SECONDS);

        ScheduledFuture<?> future = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(() -> {
            SseEmitter emitter = sseEmitterMap.get(correlationId);
            if (emitter != null) {
                try {
                    log.info("發送心跳到 SSE 連線，關聯 ID: {}", correlationId);
                    TaskEvent heartbeatEvent = TaskEvent.builder()
                            .correlationId(correlationId)
                            .status("HEARTBEAT")
                            .message("基金系統心跳檢測")
                            .finalEvent(false)
                            .build();

                    // 直接發送心跳事件
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(System.currentTimeMillis()))
                            .name("HEARTBEAT")
                            .data(heartbeatEvent)
                            .reconnectTime(0));

                    log.info("心跳事件已發送，關聯 ID: {}", correlationId);
                } catch (IOException e) {
                    log.error("發送心跳到關聯 ID 為 {} 的 SSE 時出錯: {}", correlationId, e.getMessage());
                    stopHeartbeat(correlationId);
                    sseEmitterMap.remove(correlationId);
                    emitter.completeWithError(e);
                }
            } else {
                // 如果emitter不存在，則停止心跳
                log.info("找不到關聯 ID 為 {} 的 SSE emitter，停止心跳", correlationId);
                stopHeartbeat(correlationId);
            }
        }, 2, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS); // 2秒後開始，然後每10秒一次

        heartbeatFutureMap.put(correlationId, future);
    }

    private void stopHeartbeat(String correlationId) {
        ScheduledFuture<?> future = heartbeatFutureMap.remove(correlationId);
        if (future != null) {
            future.cancel(false);
            log.info("已停止關聯 ID 為 {} 的心跳", correlationId);
        }
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
                int sleepSeconds = ThreadLocalRandom.current().nextInt(3, 11); // [3-10]
                TimeUnit.SECONDS.sleep(sleepSeconds);
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

    private String extractSseConnectionIdFromSingleTaskId(String singleTaskId) {
        if (singleTaskId == null)
            return null;
        Matcher matcher = SSE_CONNECTION_ID_EXTRACTOR_PATTERN.matcher(singleTaskId);
        if (matcher.matches()) {
            return matcher.group(1); // 返回捕獲組1，即 xxx-fund 或 xxx-bond
        }
        // 如果模式不匹配 (例如，它本身就是一個 sseConnectionId 而不是單個任務 ID)，則返回原始 ID
        // 這種情況可能發生在直接操作 SSE 連線 ID 的事件上 (雖然目前設計中事件都帶有單任務ID)
        log.warn("無法從 {} 中提取 SSE 連線 ID，將使用原 ID。這可能表示一個非預期的事件ID格式。", singleTaskId);
        return singleTaskId;
    }

    public void handleEvent(TaskEvent event) {
        String singleTaskId = event.getCorrelationId(); // 事件的 correlationId 是單個任務的 ID
        log.info("Kafka 監聽器收到事件，單任務 ID: {}, 狀態: {}", singleTaskId, event.getStatus());

        String sseConnectionId = extractSseConnectionIdFromSingleTaskId(singleTaskId);
        if (sseConnectionId == null) {
            log.error("無法從單任務 ID {} 提取 SSE 連線 ID，忽略事件。", singleTaskId);
            return;
        }
        log.info("從單任務 ID {} 提取到 SSE 連線 ID: {}", singleTaskId, sseConnectionId);

        SseEmitter emitter = sseEmitterMap.get(sseConnectionId);

        if (emitter != null) {
            try {
                log.debug("向 SSE 連線 {} (Emitter: {}) 發送事件: {}", sseConnectionId, emitter, event);
                emitter.send(SseEmitter.event()
                        .id(singleTaskId + "-" + System.currentTimeMillis()) // 事件ID可以更具體
                        .name(event.getStatus()) // 事件名稱用狀態
                        .data(event)); // 發送完整的 TaskEvent 物件
                log.info("已向 SSE 連線 {} 發送事件，單任務 ID: {}, 狀態: {}", sseConnectionId, singleTaskId, event.getStatus());

                // 檢查是否需要關閉 SSE 連線
                List<String> trackedTaskIds = sseConnectionTaskIdsMap.get(sseConnectionId);
                if (trackedTaskIds != null && !trackedTaskIds.isEmpty()) {
                    // 此 SSE 連線正在追蹤一組任務
                    if (event.isFinalEvent()) {
                        log.info("單任務 {} (屬於 SSE 連線 {}) 已完成 (finalEvent=true)", singleTaskId, sseConnectionId);
                        Set<String> completedTasks = sseConnectionCompletedTasksMap.computeIfAbsent(sseConnectionId,
                                k -> new HashSet<>());
                        completedTasks.add(singleTaskId);
                        log.info("SSE 連線 {} 的已完成任務列表: {}", sseConnectionId, completedTasks);

                        // 檢查是否所有被追蹤的任務都已完成
                        if (completedTasks.containsAll(trackedTaskIds) && trackedTaskIds.containsAll(completedTasks)) {
                            log.info("SSE 連線 {} 的所有追蹤任務均已完成。準備關閉 SSE 連線。", sseConnectionId);
                            emitter.send(SseEmitter.event().name("ALL_TASKS_COMPLETED").data(
                                    TaskEvent.builder()
                                            .correlationId(sseConnectionId) // 使用 SSE 連線 ID 作為此總結事件的 ID
                                            .status("ALL_TASKS_COMPLETED")
                                            .message("所有為此SSE連線追蹤的任務已處理完畢")
                                            .finalEvent(true)
                                            .build()));
                            emitter.complete();
                            // cleanupSseResources 已經在 emitter.onCompletion 中調用，所以這裡不需要再次調用
                        } else {
                            log.info("SSE 連線 {} 尚有未完成的任務。追蹤任務: {}, 已完成任務: {}",
                                    sseConnectionId, trackedTaskIds, completedTasks);
                        }
                    }
                } else if (event.isFinalEvent()) {
                    // 此 SSE 連線不追蹤特定任務列表 (例如，舊的行為或 taskIds 為空)
                    // 或者這個事件的 finalEvent 是針對整個 SSE 連線的 (這需要前端/事件發布者明確指定)
                    log.info("SSE 連線 {} 不追蹤特定任務列表，或收到針對整個連線的 finalEvent。單任務 {} 完成，準備關閉 SSE 連線。", sseConnectionId,
                            singleTaskId);
                    emitter.complete();
                    // cleanupSseResources 會在 onCompletion 中調用
                }

            } catch (IOException e) {
                log.error("向 SSE 連線 {} 發送事件 {} 時出錯: {}", sseConnectionId, event, e.getMessage(), e);
                // emitter.completeWithError(e); // onError 回調會處理清理
            } catch (Exception e) {
                log.error("處理 SSE 連線 {} 的事件 {} 時發生意外錯誤: {}", sseConnectionId, event, e.getMessage(), e);
                // emitter.completeWithError(e);
            }
        } else {
            log.warn("找不到 SSE 連線 ID {} 對應的 SseEmitter。事件 {} 可能無法發送或已被處理。", sseConnectionId, event);
        }
    }

    // 新增: 清理 SSE 相關資源的輔助方法
    private void cleanupSseResources(String sseConnectionId) {
        sseEmitterMap.remove(sseConnectionId);
        stopHeartbeat(sseConnectionId); // 確保心跳也被停止和移除
        sseConnectionTaskIdsMap.remove(sseConnectionId);
        sseConnectionCompletedTasksMap.remove(sseConnectionId);
        log.info("已清理 SSE 連線 {} 的所有相關資源", sseConnectionId);
    }
}